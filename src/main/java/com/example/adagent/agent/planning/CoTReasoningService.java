package com.example.adagent.agent.planning;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class CoTReasoningService {

    private static final Logger logger = LoggerFactory.getLogger(CoTReasoningService.class);
    private final ChatClient chatClient;

    public CoTReasoningService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public PlanningService.ExecutionPlan createCoTPlan(String intentType, String userInput) {
        logger.info("【推理规划层-CoT】意图: {}", intentType);
        List<String> steps = Arrays.asList(
            "步骤1：理解用户问题",
            "步骤2：分析要点",
            "步骤3：组织回答",
            "步骤4：生成回复"
        );
        return new PlanningService.ExecutionPlan(
            "CoT",
            steps,
            new ArrayList<>(),
            "CoT推理：无需工具，直接回答"
        );
    }
}
