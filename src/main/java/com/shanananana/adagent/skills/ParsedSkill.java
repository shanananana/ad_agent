package com.shanananana.adagent.skills;

public record ParsedSkill(
        String id,
        String name,
        String description,
        String inputSchemaJson,
        String bodyMarkdown,
        String sourceDescription
) {
}
