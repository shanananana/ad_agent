package com.shanananana.adagent.skills;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 技能扫描与工具返回长度限制。
 */
@ConfigurationProperties(prefix = "spring.ai.markdown-skills")
public class MarkdownSkillsProperties {

    private boolean enabled = true;

    /**
     * 资源模式；使用 classpath* 可合并依赖 jar 中的 skills。
     */
    private List<String> locations = new ArrayList<>(List.of("classpath*:skills/**/*.md"));

    private int maxBodyChars = 12_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public int getMaxBodyChars() {
        return maxBodyChars;
    }

    public void setMaxBodyChars(int maxBodyChars) {
        this.maxBodyChars = maxBodyChars;
    }
}
