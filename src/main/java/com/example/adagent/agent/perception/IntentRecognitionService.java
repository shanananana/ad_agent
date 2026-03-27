package com.example.adagent.agent.perception;

import com.example.adagent.prompt.ClasspathPromptLoader;
import com.example.adagent.prompt.PromptResourcePaths;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 广告域<strong>意图识别</strong>：调用 LLM 判定用户本轮意图类型（效果/基础数据/加计划/改策略/
 * 清除长期记忆或聊天记录等），并输出是否需走工具链；提示中注入长期记忆与最近对话以识别简短确认类回复。
 * <p>优先将模型输出按 JSON 解析为结构化结果，失败时降级为正则解析，再降级为关键词规则。</p>
 */
@Service
public class IntentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognitionService.class);
    private final ChatClient chatClient;
    private final PromptTemplate intentWithMemoryPromptTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern INTENT_PATTERN = Pattern.compile("\"intentType\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEEDS_TOOL_PATTERN = Pattern.compile("\"needsTool\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOL_PATTERN = Pattern.compile("\"(userExpressedSegmentPreference|needClarification|segmentByAge|segmentByRegion|segmentByDevice)\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUGGESTED_MSG_PATTERN = Pattern.compile("\"suggestedClarificationMessage\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    public IntentRecognitionService(
            @Qualifier("noToolChatClient") ChatClient noToolChatClient,
            ClasspathPromptLoader classpathPromptLoader,
            ObjectMapper objectMapper) {
        this.chatClient = noToolChatClient;
        this.intentWithMemoryPromptTemplate = classpathPromptLoader.loadTemplate(PromptResourcePaths.INTENT_WITH_MEMORY);
        this.objectMapper = objectMapper;
    }

    public IntentResult recognizeIntent(String userInput) {
        return recognizeIntent(userInput, null, null);
    }

    /** 带长期记忆的意图识别（编排器传入 longTermContext，用于加计划时是否拆分及追问话术） */
    public IntentResult recognizeIntent(String userInput, String longTermContext) {
        return recognizeIntent(userInput, longTermContext, null);
    }

    /**
     * 带长期记忆与最近对话的意图识别。
     * 编排器传入 conversationContext（短期会话摘要）后，可结合上一轮助手提问识别用户简短确认（如「需要」「好的」）为对应意图。
     */
    public IntentResult recognizeIntent(String userInput, String longTermContext, String conversationContext) {
        try {
            logger.info("【感知理解层】开始识别意图: {}", userInput);
            String memoryBlock = (longTermContext != null && !longTermContext.isBlank())
                    ? longTermContext
                    : "（该用户暂无长期记忆）";
            String conversationBlock = (conversationContext != null && !conversationContext.isBlank())
                    ? conversationContext
                    : "（无最近对话）";
            Prompt prompt = intentWithMemoryPromptTemplate.create(Map.of(
                    "userInput", userInput,
                    "longTermContext", memoryBlock,
                    "conversationContext", conversationBlock));
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            String content = response.getResult().getOutput().getText();
            logger.info("【感知理解层】意图识别原始: {}", content);
            return parseIntentResult(content, userInput, true);
        } catch (Exception e) {
            logger.error("【感知理解层】意图识别失败", e);
            return parseIntentByKeywords(userInput);
        }
    }

    private IntentResult parseIntentResult(String content, String userInput, boolean parseSegmentFields) {
        IntentResult structured = tryParseStructuredJson(content, userInput);
        if (structured != null) {
            return structured;
        }
        String intentType = null;
        Matcher im = INTENT_PATTERN.matcher(content);
        if (im.find()) {
            intentType = im.group(1).toUpperCase();
            if (!intentType.startsWith("INTENT_")) {
                intentType = "INTENT_" + intentType;
            }
        }
        boolean needsTool = true;
        Matcher tm = NEEDS_TOOL_PATTERN.matcher(content);
        if (tm.find()) {
            needsTool = "true".equalsIgnoreCase(tm.group(1));
        }
        if (intentType == null) {
            return parseIntentByKeywords(userInput);
        }
        if ("INTENT_OTHER".equals(intentType) && !needsTool) {
            return new IntentResult(intentType, false, userInput);
        }
        if (!parseSegmentFields || !"INTENT_ADD_CAMPAIGN".equals(intentType)) {
            return new IntentResult(intentType, needsTool, userInput);
        }
        Boolean segmentByAge = null;
        Boolean segmentByRegion = null;
        Boolean segmentByDevice = null;
        boolean userExpressedSegmentPreference = false;
        boolean needClarification = false;
        String suggestedClarificationMessage = "";
        Matcher bm = BOOL_PATTERN.matcher(content);
        while (bm.find()) {
            String key = bm.group(1).toLowerCase();
            boolean val = "true".equalsIgnoreCase(bm.group(2));
            if ("userexpressedsegmentpreference".equals(key)) {
                userExpressedSegmentPreference = val;
            } else if ("needclarification".equals(key)) {
                needClarification = val;
            } else if ("segmentbyage".equals(key)) {
                segmentByAge = val;
            } else if ("segmentbyregion".equals(key)) {
                segmentByRegion = val;
            } else if ("segmentbydevice".equals(key)) {
                segmentByDevice = val;
            }
        }
        Matcher sm = SUGGESTED_MSG_PATTERN.matcher(content);
        if (sm.find()) {
            suggestedClarificationMessage = unescapeJsonString(sm.group(1));
        }
        return new IntentResult(intentType, needsTool, userInput,
                segmentByAge, segmentByRegion, segmentByDevice,
                userExpressedSegmentPreference, needClarification, suggestedClarificationMessage);
    }

    /**
     * 从模型输出中提取 JSON 对象并反序列化；失败返回 null 以触发正则/关键词降级。
     */
    private IntentResult tryParseStructuredJson(String raw, String userInput) {
        String json = extractJsonPayload(raw);
        if (json == null) {
            return null;
        }
        try {
            IntentLlmJson o = objectMapper.readValue(json, IntentLlmJson.class);
            if (o.intentType == null || o.intentType.isBlank() || o.needsTool == null) {
                return null;
            }
            String intentType = normalizeIntentType(o.intentType.trim());
            boolean needsTool = o.needsTool;
            if ("INTENT_OTHER".equals(intentType) && !needsTool) {
                return new IntentResult(intentType, false, userInput);
            }
            if (!"INTENT_ADD_CAMPAIGN".equals(intentType)) {
                return new IntentResult(intentType, needsTool, userInput);
            }
            String suggested = o.suggestedClarificationMessage != null ? o.suggestedClarificationMessage : "";
            return new IntentResult(intentType, needsTool, userInput,
                    o.segmentByAge, o.segmentByRegion, o.segmentByDevice,
                    Boolean.TRUE.equals(o.userExpressedSegmentPreference),
                    Boolean.TRUE.equals(o.needClarification),
                    suggested);
        } catch (Exception e) {
            logger.debug("【感知理解层】结构化 JSON 解析失败，使用正则降级: {}", e.getMessage());
            return null;
        }
    }

    private static String extractJsonPayload(String content) {
        if (content == null) {
            return null;
        }
        String s = content.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) {
                s = s.substring(nl + 1);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return s.substring(start, end + 1);
    }

    private static String normalizeIntentType(String raw) {
        String t = raw.toUpperCase();
        if (!t.startsWith("INTENT_")) {
            t = "INTENT_" + t;
        }
        return t;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class IntentLlmJson {
        public String intentType;
        public Boolean needsTool;
        public Boolean segmentByAge;
        public Boolean segmentByRegion;
        public Boolean segmentByDevice;
        public Boolean userExpressedSegmentPreference;
        public Boolean needClarification;
        public String suggestedClarificationMessage;
    }

    private static String unescapeJsonString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\n", "\n").replace("\\\"", "\"");
    }

    private IntentResult parseIntentByKeywords(String userInput) {
        String lower = userInput == null ? "" : userInput.toLowerCase();
        if (lower.contains("效果") || lower.contains("点击率") || lower.contains("roi") || lower.contains("消耗") || lower.contains("投放数据") || lower.contains("表现")) {
            return new IntentResult("INTENT_PERFORMANCE_QUERY", true, userInput);
        }
        if (lower.contains("计划列表") || lower.contains("有哪些计划") || lower.contains("广告列表") || lower.contains("素材") || lower.contains("计划详情")) {
            return new IntentResult("INTENT_BASE_DATA_QUERY", true, userInput);
        }
        if (lower.contains("加一个计划") || lower.contains("新建计划") || lower.contains("创建计划") || lower.contains("加一个投放计划")) {
            return new IntentResult("INTENT_ADD_CAMPAIGN", true, userInput);
        }
        if (lower.contains("改预算") || lower.contains("暂停") || lower.contains("启用") || lower.contains("调整策略") || lower.contains("日预算")) {
            return new IntentResult("INTENT_STRATEGY_ADJUST", true, userInput);
        }
        boolean wantsClear = lower.contains("清除") || lower.contains("删除") || lower.contains("清空");
        if (wantsClear && lower.contains("长期记忆") && (lower.contains("聊天") || lower.contains("对话") || lower.contains("聊天记录"))) {
            return new IntentResult("INTENT_CLEAR_ALL_USER_MEMORY", true, userInput);
        }
        if (wantsClear && lower.contains("全部") && (lower.contains("记忆") || lower.contains("隐私") || lower.contains("数据"))) {
            return new IntentResult("INTENT_CLEAR_ALL_USER_MEMORY", true, userInput);
        }
        if (wantsClear && (lower.contains("聊天") || lower.contains("对话") || lower.contains("聊天记录"))) {
            return new IntentResult("INTENT_CLEAR_CHAT_HISTORY", true, userInput);
        }
        if (wantsClear && (lower.contains("长期记忆") || lower.contains("习惯记忆") || (lower.contains("偏好") && lower.contains("习惯")))) {
            return new IntentResult("INTENT_CLEAR_LONG_TERM_MEMORY", true, userInput);
        }
        return new IntentResult("INTENT_OTHER", false, userInput);
    }

    public static class IntentResult {
        private final String intentType;
        private final boolean needsTool;
        private final String originalInput;
        /** 是否按年龄拆分（仅 INTENT_ADD_CAMPAIGN 时有效；null=用户未提及） */
        private final Boolean segmentByAge;
        private final Boolean segmentByRegion;
        private final Boolean segmentByDevice;
        /** 用户是否明确表达了是否拆分（如「按年龄拆」「不拆」） */
        private final boolean userExpressedSegmentPreference;
        /** 是否需要追问（加计划且未明确拆分意向时） */
        private final boolean needClarification;
        /** 追问话术（含长期记忆中的预算占比策略） */
        private final String suggestedClarificationMessage;

        public IntentResult(String intentType, boolean needsTool, String originalInput) {
            this(intentType, needsTool, originalInput, null, null, null, false, false, null);
        }

        public IntentResult(String intentType, boolean needsTool, String originalInput,
                           Boolean segmentByAge, Boolean segmentByRegion, Boolean segmentByDevice,
                           boolean userExpressedSegmentPreference, boolean needClarification,
                           String suggestedClarificationMessage) {
            this.intentType = intentType;
            this.needsTool = needsTool;
            this.originalInput = originalInput;
            this.segmentByAge = segmentByAge;
            this.segmentByRegion = segmentByRegion;
            this.segmentByDevice = segmentByDevice;
            this.userExpressedSegmentPreference = userExpressedSegmentPreference;
            this.needClarification = needClarification;
            this.suggestedClarificationMessage = suggestedClarificationMessage != null ? suggestedClarificationMessage : "";
        }

        public static IntentResult defaultIntent(String userInput) {
            return new IntentResult("INTENT_OTHER", false, userInput);
        }

        public String getIntentType() {
            return intentType;
        }

        public boolean isNeedsTool() {
            return needsTool;
        }

        public String getOriginalInput() {
            return originalInput;
        }

        public Boolean getSegmentByAge() {
            return segmentByAge;
        }

        public Boolean getSegmentByRegion() {
            return segmentByRegion;
        }

        public Boolean getSegmentByDevice() {
            return segmentByDevice;
        }

        public boolean isUserExpressedSegmentPreference() {
            return userExpressedSegmentPreference;
        }

        public boolean isNeedClarification() {
            return needClarification;
        }

        public String getSuggestedClarificationMessage() {
            return suggestedClarificationMessage;
        }

        @Override
        public String toString() {
            return "IntentResult{intentType='" + intentType + "', needsTool=" + needsTool
                    + ", needClarification=" + needClarification + "}";
        }
    }
}
