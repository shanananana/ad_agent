package com.shanananana.adagent.skills;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public final class MarkdownSkillRegistry {

    private final List<ToolCallback> toolCallbacks;
    private final List<SkillSummary> summaries;

    public MarkdownSkillRegistry(List<ToolCallback> toolCallbacks, List<SkillSummary> summaries) {
        this.toolCallbacks = toolCallbacks;
        this.summaries = summaries;
    }

    public List<ToolCallback> getToolCallbacks() {
        return toolCallbacks;
    }

    public List<SkillSummary> getSummaries() {
        return summaries;
    }
}
