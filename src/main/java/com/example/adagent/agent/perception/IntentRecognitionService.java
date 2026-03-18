package com.example.adagent.agent.perception;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 广告域意图识别：效果查询、基础数据查询、加计划、策略调整等。
 */
@Service
public class IntentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognitionService.class);
    private final ChatClient chatClient;
    private final PromptTemplate intentPromptTemplate;
    private final PromptTemplate intentWithMemoryPromptTemplate;

    private static final Pattern INTENT_PATTERN = Pattern.compile("\"intentType\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEEDS_TOOL_PATTERN = Pattern.compile("\"needsTool\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOL_PATTERN = Pattern.compile("\"(userExpressedSegmentPreference|needClarification|segmentByAge|segmentByRegion|segmentByDevice)\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUGGESTED_MSG_PATTERN = Pattern.compile("\"suggestedClarificationMessage\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    public IntentRecognitionService(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.intentPromptTemplate = new PromptTemplate("""
            你正在为广告投放 Agent 做意图识别。用户可能：查投放效果、查计划/广告/素材列表、加一个投放计划、调整策略（改预算/暂停）等。
            
            需要调用工具的类型（needsTool=true）：
            - 投放效果查询：查效果、点击率、ROI、消耗、各计划/素材/渠道/年龄表现（intentType 填 INTENT_PERFORMANCE_QUERY）
            - 基础数据查询：有哪些计划、计划列表、广告列表、素材列表、计划详情（intentType 填 INTENT_BASE_DATA_QUERY）
            - 加投放计划：加一个计划、新建计划、创建计划（intentType 填 INTENT_ADD_CAMPAIGN）
            - 策略调整：改预算、暂停计划、启用计划、调高日预算（intentType 填 INTENT_STRATEGY_ADJUST）
            
            不需要工具（needsTool=false）：纯闲聊、概念解释等。intentType 填 INTENT_OTHER。
            
            用户输入: {userInput}
            
            返回 JSON，包含 intentType（INTENT_PERFORMANCE_QUERY/INTENT_BASE_DATA_QUERY/INTENT_ADD_CAMPAIGN/INTENT_STRATEGY_ADJUST/INTENT_OTHER）、needsTool（true/false）。
            """);
        this.intentWithMemoryPromptTemplate = new PromptTemplate("""
            你正在为广告投放 Agent 做意图识别。用户可能：查投放效果、查计划/广告/素材列表、加一个投放计划、调整策略（改预算/暂停）等。
            
            需要调用工具的类型（needsTool=true）：
            - 投放效果查询：intentType 填 INTENT_PERFORMANCE_QUERY
            - 基础数据查询：intentType 填 INTENT_BASE_DATA_QUERY
            - 加投放计划：intentType 填 INTENT_ADD_CAMPAIGN
            - 策略调整：intentType 填 INTENT_STRATEGY_ADJUST
            不需要工具（needsTool=false）：纯闲聊、概念解释等。intentType 填 INTENT_OTHER。
            
            结合【最近对话】识别简短确认：若存在【最近对话】，且用户当前输入是对上一轮助手提问的简短肯定（如「需要」「好的」「可以」「行」「创建」「确认」等），必须结合最近对话判断意图。例如：上一轮助手问「是否立即为您创建这个计划」「是否需要按某方式创建」等且用户表示肯定，则 intentType 填 INTENT_ADD_CAMPAIGN，needsTool 填 true；上一轮在问是否查询某计划效果等且用户肯定，则填对应查询类意图。不要仅因用户输入过短就判为 INTENT_OTHER。
            
            【该用户的历史偏好/习惯】
            {longTermContext}
            
            【最近对话】
            {conversationContext}
            
            用户输入: {userInput}
            
            返回 JSON，必须包含：intentType、needsTool。若为 INTENT_ADD_CAMPAIGN 还需包含：userExpressedSegmentPreference（true/false）、needClarification（true/false）、suggestedClarificationMessage（字符串，追问话术；若无需追问则填空字符串""）。若用户明确说了拆分维度，可包含 segmentByAge、segmentByRegion、segmentByDevice（true/false）。
            """);
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

    private IntentResult parseIntentResult(String content, String userInput) {
        return parseIntentResult(content, userInput, false);
    }

    private IntentResult parseIntentResult(String content, String userInput, boolean parseSegmentFields) {
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
            if ("userexpressedsegmentpreference".equals(key)) userExpressedSegmentPreference = val;
            else if ("needclarification".equals(key)) needClarification = val;
            else if ("segmentbyage".equals(key)) segmentByAge = val;
            else if ("segmentbyregion".equals(key)) segmentByRegion = val;
            else if ("segmentbydevice".equals(key)) segmentByDevice = val;
        }
        Matcher sm = SUGGESTED_MSG_PATTERN.matcher(content);
        if (sm.find()) {
            suggestedClarificationMessage = unescapeJsonString(sm.group(1));
        }
        return new IntentResult(intentType, needsTool, userInput,
                segmentByAge, segmentByRegion, segmentByDevice,
                userExpressedSegmentPreference, needClarification, suggestedClarificationMessage);
    }

    private static String unescapeJsonString(String s) {
        if (s == null) return "";
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

        public String getIntentType() { return intentType; }
        public boolean isNeedsTool() { return needsTool; }
        public String getOriginalInput() { return originalInput; }
        public Boolean getSegmentByAge() { return segmentByAge; }
        public Boolean getSegmentByRegion() { return segmentByRegion; }
        public Boolean getSegmentByDevice() { return segmentByDevice; }
        public boolean isUserExpressedSegmentPreference() { return userExpressedSegmentPreference; }
        public boolean isNeedClarification() { return needClarification; }
        public String getSuggestedClarificationMessage() { return suggestedClarificationMessage; }

        @Override
        public String toString() {
            return "IntentResult{intentType='" + intentType + "', needsTool=" + needsTool
                    + ", needClarification=" + needClarification + "}";
        }
    }
}
