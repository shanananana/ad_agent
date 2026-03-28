package com.shanananana.adagent.skills;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillContextInput(
        String context
) {
    public SkillContextInput {
        if (context == null) {
            context = "";
        }
    }
}
