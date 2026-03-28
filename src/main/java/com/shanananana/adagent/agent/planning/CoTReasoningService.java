package com.shanananana.adagent.agent.planning;

import com.shanananana.adagent.prompt.ClasspathPromptLoader;
import com.shanananana.adagent.prompt.PromptResourcePaths;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <strong>链式思考（CoT）规划</strong>：当本轮判定为无需调用工具时，从 classpath 提示加载推理步骤与说明文案，
 * 组装为 {@link PlanningService.ExecutionPlan}，供前端展示「思考过程」及后续纯文本生成。
 */
@Service
public class CoTReasoningService {

    private static final Logger logger = LoggerFactory.getLogger(CoTReasoningService.class);
    private final ClasspathPromptLoader classpathPromptLoader;

    public CoTReasoningService(ClasspathPromptLoader classpathPromptLoader) {
        this.classpathPromptLoader = classpathPromptLoader;
    }

    public PlanningService.ExecutionPlan createCoTPlan(String intentType, String userInput) {
        logger.info("【推理规划层-CoT】意图: {}", intentType);
        String raw = classpathPromptLoader.loadText(PromptResourcePaths.PLANNING_COT_STEPS);
        List<String> steps = Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        String reasoning = classpathPromptLoader.loadText(PromptResourcePaths.PLANNING_COT_REASONING).trim();
        return new PlanningService.ExecutionPlan(
                "CoT",
                steps,
                new ArrayList<>(),
                reasoning
        );
    }
}
