package com.shanananana.adagent.skills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MarkdownSkillToolFactory {

    private static final Logger log = LoggerFactory.getLogger(MarkdownSkillToolFactory.class);

    private static final String DEFAULT_CONTEXT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "context": {
                  "type": "string",
                  "description": "用户意图或补充上下文"
                }
              },
              "additionalProperties": false
            }
            """;

    private final ObjectMapper objectMapper;

    public MarkdownSkillToolFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MarkdownSkillRegistry buildRegistry(List<ParsedSkill> skills, int maxBodyChars) {
        List<ToolCallback> callbacks = new ArrayList<>();
        List<SkillSummary> summaries = new ArrayList<>();
        Set<String> usedToolNames = new LinkedHashSet<>();
        for (ParsedSkill skill : skills) {
            String toolName = ToolNames.sanitize(skill.id());
            if (!usedToolNames.add(toolName)) {
                log.error("工具名冲突「{}」，跳过：{}", toolName, skill.sourceDescription());
                continue;
            }
            try {
                callbacks.add(createCallback(skill, maxBodyChars));
            } catch (Exception e) {
                log.error("构建工具失败 skill={} {}：{}", skill.id(), skill.sourceDescription(), e.getMessage());
                usedToolNames.remove(toolName);
                continue;
            }
            summaries.add(new SkillSummary(
                    skill.id(),
                    toolName,
                    skill.name(),
                    skill.description(),
                    skill.sourceDescription()));
        }
        return new MarkdownSkillRegistry(List.copyOf(callbacks), List.copyOf(summaries));
    }

    private ToolCallback createCallback(ParsedSkill skill, int maxBodyChars) {
        String toolName = ToolNames.sanitize(skill.id());
        if (skill.inputSchemaJson() == null) {
            return FunctionToolCallback.builder(toolName, (SkillContextInput input) -> invoke(skill, input, maxBodyChars))
                    .description(skill.description())
                    .inputType(SkillContextInput.class)
                    .inputSchema(DEFAULT_CONTEXT_SCHEMA)
                    .build();
        }
        return FunctionToolCallback.builder(
                        toolName,
                        (Map<String, Object> input) -> invokeMap(skill, input, maxBodyChars))
                .description(skill.description())
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .inputSchema(skill.inputSchemaJson())
                .build();
    }

    private String invoke(ParsedSkill skill, SkillContextInput input, int maxBodyChars) {
        try {
            String ctx = input == null ? "" : input.context();
            String userJson = objectMapper.writeValueAsString(Map.of("context", ctx == null ? "" : ctx));
            return buildResultJson(skill, userJson, maxBodyChars);
        } catch (JsonProcessingException e) {
            return safeError(e.getMessage());
        }
    }

    private String invokeMap(ParsedSkill skill, Map<String, Object> input, int maxBodyChars) {
        try {
            String userJson = objectMapper.writeValueAsString(input != null ? input : Map.of());
            return buildResultJson(skill, userJson, maxBodyChars);
        } catch (JsonProcessingException e) {
            return safeError(e.getMessage());
        }
    }

    private String buildResultJson(ParsedSkill skill, String userInputJson, int maxBodyChars)
            throws JsonProcessingException {
        String body = skill.bodyMarkdown() == null ? "" : skill.bodyMarkdown();
        boolean truncated = body.length() > maxBodyChars;
        if (truncated) {
            body = body.substring(0, maxBodyChars) + "\n\n...[truncated]";
        }
        return objectMapper.writeValueAsString(Map.of(
                "skillId", skill.id(),
                "skillName", skill.name(),
                "bodyMarkdown", body,
                "truncated", truncated,
                "userInput", userInputJson));
    }

    private String safeError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "error", "markdown-skill-tool",
                    "message", message == null ? "" : message));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"markdown-skill-tool\"}";
        }
    }
}
