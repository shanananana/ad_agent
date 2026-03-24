package com.example.adagent.agent.planning;

/**
 * 将 {@link PlanningService.ExecutionPlan} 格式化为可追加到 system 提示中的正文。
 */
public final class ExecutionPlanFormatting {

    private ExecutionPlanFormatting() {
    }

    /**
     * @return 非空则供 Advisor 注入；无实质内容时返回空串（Advisor 将跳过追加）
     */
    public static String toSystemAppendix(PlanningService.ExecutionPlan plan) {
        if (plan == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("规划类型: ").append(nullToEmpty(plan.getPlanType())).append("\n");
        if (plan.getRequiredTools() != null && !plan.getRequiredTools().isEmpty()) {
            sb.append("建议工具: ").append(String.join(", ", plan.getRequiredTools())).append("\n");
        }
        if (plan.getSteps() != null && !plan.getSteps().isEmpty()) {
            sb.append("步骤:\n");
            for (int i = 0; i < plan.getSteps().size(); i++) {
                sb.append(i + 1).append(". ").append(plan.getSteps().get(i)).append("\n");
            }
        }
        String reasoning = plan.getReasoning();
        if (reasoning != null && !reasoning.isBlank()) {
            sb.append("说明: ").append(reasoning.trim()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
