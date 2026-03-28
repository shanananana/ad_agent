package com.shanananana.adagent.creative;

import com.shanananana.adagent.prompt.ClasspathPromptLoader;
import com.shanananana.adagent.prompt.PromptResourcePaths.Placement;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * <strong>版位 → 提示文案</strong>：将前端选择的版位代码映射为 classpath 中的画幅、安全区与构图约束说明，
 * 供 {@link CreativePromptSuggestService} 的 {@code PromptTemplate} 占位符注入；文案位于 {@code prompts/placement/*.txt}。
 */
@Component
public class CreativePlacementPromptSpec {

    private final ClasspathPromptLoader classpathPromptLoader;

    public CreativePlacementPromptSpec(ClasspathPromptLoader classpathPromptLoader) {
        this.classpathPromptLoader = classpathPromptLoader;
    }

    /**
     * @param placement 如 FEED、SPLASH、BANNER；空则返回通用说明
     */
    public String build(String placement) {
        if (!StringUtils.hasText(placement)) {
            return classpathPromptLoader.loadText(Placement.DEFAULT);
        }
        String p = placement.trim().toUpperCase();
        return switch (p) {
            case "FEED", "FEED_CARD", "NATIVE" -> classpathPromptLoader.loadText(Placement.FEED);
            case "SPLASH", "SPLASH_SCREEN", "OPENING" -> classpathPromptLoader.loadText(Placement.SPLASH);
            case "BANNER", "TOP_BANNER", "HORIZONTAL" -> classpathPromptLoader.loadText(Placement.BANNER);
            case "VIDEO_COVER", "VIDEO" -> classpathPromptLoader.loadText(Placement.VIDEO_COVER);
            default -> classpathPromptLoader.renderTemplate(Placement.OTHER, Map.of("placementCode", p));
        };
    }
}
