package com.example.adagent.agent.planning;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanningService {

    private static final Logger logger = LoggerFactory.getLogger(PlanningService.class);
    private final ChatClient chatClient;
    private final CoTReasoningService cotReasoningService;
    private final ReActService reactService;

    public PlanningService(ChatClient chatClient, CoTReasoningService cotReasoningService, ReActService reactService) {
        this.chatClient = chatClient;
        this.cotReasoningService = cotReasoningService;
        this.reactService = reactService;
    }

    public ExecutionPlan createPlan(String intentType, String userInput, boolean needsTool) {
        logger.info("【推理规划层】意图: {}, needsTool: {}", intentType, needsTool);
        if (needsTool) {
            return reactService.createReActPlan(intentType, userInput);
        }
        return cotReasoningService.createCoTPlan(intentType, userInput);
    }

    public static class ExecutionPlan {
        private final String planType;
        private final List<String> steps;
        private final List<String> requiredTools;
        private final String reasoning;

        public ExecutionPlan(String planType, List<String> steps, List<String> requiredTools, String reasoning) {
            this.planType = planType;
            this.steps = steps != null ? steps : new ArrayList<>();
            this.requiredTools = requiredTools != null ? requiredTools : new ArrayList<>();
            this.reasoning = reasoning;
        }

        public String getPlanType() { return planType; }
        public List<String> getSteps() { return steps; }
        public List<String> getRequiredTools() { return requiredTools; }
        public String getReasoning() { return reasoning; }

        @Override
        public String toString() {
            return "ExecutionPlan{planType='" + planType + "', requiredTools=" + requiredTools + "}";
        }
    }
}
