package com.example.adagent.agent.planning;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReActService {

    private static final Logger logger = LoggerFactory.getLogger(ReActService.class);
    private final ChatClient chatClient;

    private static final Map<String, List<String>> INTENT_TO_TOOLS = new HashMap<>();
    static {
        INTENT_TO_TOOLS.put("INTENT_PERFORMANCE_QUERY", Arrays.asList("queryPerformance"));
        INTENT_TO_TOOLS.put("INTENT_BASE_DATA_QUERY", Arrays.asList("queryBaseData"));
        INTENT_TO_TOOLS.put("INTENT_ADD_CAMPAIGN", Arrays.asList("addCampaign"));
        INTENT_TO_TOOLS.put("INTENT_STRATEGY_ADJUST", Arrays.asList("adjustStrategy"));
    }

    public ReActService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public PlanningService.ExecutionPlan createReActPlan(String intentType, String userInput) {
        logger.info("【推理规划层-ReAct】意图: {}", intentType);
        List<String> tools = INTENT_TO_TOOLS.getOrDefault(intentType, new ArrayList<>());
        List<String> steps = Arrays.asList(
            "步骤1：思考 - 分析用户需求，选择工具",
            "步骤2：行动 - 调用工具",
            "步骤3：观察 - 获取结果",
            "步骤4：推理 - 生成答复"
        );
        return new PlanningService.ExecutionPlan(
            "ReAct",
            steps,
            tools,
            "ReAct：意图=" + intentType + ", 工具=" + tools
        );
    }
}
