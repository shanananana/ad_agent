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

    private static final Pattern INTENT_PATTERN = Pattern.compile("\"intentType\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEEDS_TOOL_PATTERN = Pattern.compile("\"needsTool\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

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
    }

    public IntentResult recognizeIntent(String userInput) {
        try {
            logger.info("【感知理解层】开始识别意图: {}", userInput);
            Prompt prompt = intentPromptTemplate.create(Map.of("userInput", userInput));
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            String content = response.getResult().getOutput().getText();
            logger.info("【感知理解层】意图识别原始: {}", content);
            return parseIntentResult(content, userInput);
        } catch (Exception e) {
            logger.error("【感知理解层】意图识别失败", e);
            return parseIntentByKeywords(userInput);
        }
    }

    private IntentResult parseIntentResult(String content, String userInput) {
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
        return new IntentResult(intentType, needsTool, userInput);
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

        public IntentResult(String intentType, boolean needsTool, String originalInput) {
            this.intentType = intentType;
            this.needsTool = needsTool;
            this.originalInput = originalInput;
        }

        public static IntentResult defaultIntent(String userInput) {
            return new IntentResult("INTENT_OTHER", false, userInput);
        }

        public String getIntentType() { return intentType; }
        public boolean isNeedsTool() { return needsTool; }
        public String getOriginalInput() { return originalInput; }

        @Override
        public String toString() {
            return "IntentResult{intentType='" + intentType + "', needsTool=" + needsTool + "}";
        }
    }
}
