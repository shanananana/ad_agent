package com.shanananana.adagent.agent.planning;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * <strong>行动规划入口</strong>：根据意图识别结果中的 {@code needsTool} 在
 * {@link ReActService}（需工具）与 {@link CoTReasoningService}（纯推理）之间选择，产出结构化执行计划。
 */
@Service
public class PlanningService {

    private static final Logger logger = LoggerFactory.getLogger(PlanningService.class);
    private final CoTReasoningService cotReasoningService;
    private final ReActService reactService;

    public PlanningService(CoTReasoningService cotReasoningService, ReActService reactService) {
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

    /**
     * 单轮规划结果：规划类型（如 CoT / ReAct）、步骤列表、所需工具 Bean 名称列表、面向展示的推理摘要。
     */
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
