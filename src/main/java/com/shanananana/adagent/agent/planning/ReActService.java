package com.shanananana.adagent.agent.planning;

import com.shanananana.adagent.prompt.ClasspathPromptLoader;
import com.shanananana.adagent.prompt.PromptResourcePaths;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <strong>ReAct 规划</strong>：将意图类型映射到一组 Spring AI 工具名，并加载 ReAct 步骤与推理模板，
 * 供需要查数、改计划或清隐私等多步工具调用的场景使用。
 */
@Service
public class ReActService {

    private static final Logger logger = LoggerFactory.getLogger(ReActService.class);

    private static final Map<String, List<String>> INTENT_TO_TOOLS = new HashMap<>();
    static {
        INTENT_TO_TOOLS.put("INTENT_PERFORMANCE_QUERY", Arrays.asList("queryPerformance"));
        INTENT_TO_TOOLS.put("INTENT_BASE_DATA_QUERY", Arrays.asList("queryBaseData"));
        INTENT_TO_TOOLS.put("INTENT_ADD_CAMPAIGN", Arrays.asList("addCampaign"));
        INTENT_TO_TOOLS.put("INTENT_STRATEGY_ADJUST", Arrays.asList("adjustStrategy"));
        INTENT_TO_TOOLS.put("INTENT_CLEAR_LONG_TERM_MEMORY", Arrays.asList("clearUserLongTermMemory"));
        INTENT_TO_TOOLS.put("INTENT_CLEAR_CHAT_HISTORY", Arrays.asList("clearUserChatHistory"));
        INTENT_TO_TOOLS.put("INTENT_CLEAR_ALL_USER_MEMORY", Arrays.asList("clearUserLongTermMemory", "clearUserChatHistory"));
    }

    private final ClasspathPromptLoader classpathPromptLoader;

    public ReActService(ClasspathPromptLoader classpathPromptLoader) {
        this.classpathPromptLoader = classpathPromptLoader;
    }

    public PlanningService.ExecutionPlan createReActPlan(String intentType, String userInput) {
        logger.info("【推理规划层-ReAct】意图: {}", intentType);
        List<String> tools = INTENT_TO_TOOLS.getOrDefault(intentType, new ArrayList<>());
        String raw = classpathPromptLoader.loadText(PromptResourcePaths.PLANNING_REACT_STEPS);
        List<String> steps = Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        String reasoning = classpathPromptLoader.renderTemplate(
                PromptResourcePaths.PLANNING_REACT_REASONING,
                Map.of("intentType", intentType, "tools", String.valueOf(tools)));
        return new PlanningService.ExecutionPlan(
                "ReAct",
                steps,
                tools,
                reasoning
        );
    }
}
