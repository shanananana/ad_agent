package com.shanananana.adagent.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自测：skills 自 classpath 加载后进入 {@link MarkdownSkillRegistry}，与 Spring AI {@code ToolCallback} 数量一致。
 */
class MarkdownSkillsLoadTest {

    @Test
    void creativeImageWorkflowSkillLoadsAsToolCallback() {
        MarkdownSkillsProperties props = new MarkdownSkillsProperties();
        props.setLocations(List.of("classpath*:skills/**/*.md"));
        ObjectMapper objectMapper = new ObjectMapper();
        MarkdownSkillLoader loader = new MarkdownSkillLoader();
        List<ParsedSkill> skills = loader.loadAll(props, new MarkdownSkillParser(objectMapper), new DefaultResourceLoader());

        assertFalse(skills.isEmpty(), "classpath*:skills/**/*.md 应至少解析出一个 skill");
        assertTrue(
                skills.stream().anyMatch(s -> "creative_image_workflow".equals(s.id())),
                "应包含 creative_image_workflow.md 中的 id");

        MarkdownSkillRegistry registry = new MarkdownSkillToolFactory(objectMapper).buildRegistry(skills, 12_000);
        assertEquals(registry.getSummaries().size(), registry.getToolCallbacks().size(), "每个 summary 对应一个 ToolCallback");
        assertTrue(
                registry.getSummaries().stream().anyMatch(s -> "creative_image_workflow".equals(s.id())),
                "registry summaries 应含 creative_image_workflow");
        assertTrue(
                registry.getSummaries().stream().anyMatch(s -> "creative_image_workflow".equals(s.toolName())),
                "tool 名 sanitize 后应与 id 一致");
    }
}
