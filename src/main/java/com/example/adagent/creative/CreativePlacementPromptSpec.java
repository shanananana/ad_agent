package com.example.adagent.creative;

import org.springframework.util.StringUtils;

/**
 * 资源位 / 版位代码 → 写入 LLM 提示中的画幅与构图约束说明（供 {@code PromptTemplate} 占位符注入）。
 */
public final class CreativePlacementPromptSpec {

    private CreativePlacementPromptSpec() {
    }

    /**
     * @param placement 如 FEED、SPLASH、BANNER；空则返回通用说明
     */
    public static String build(String placement) {
        if (!StringUtils.hasText(placement)) {
            return """
                    未指定版位：画面描述中请使用通用电商/信息流友好构图；若涉及比例，优先 1:1 或 4:5 竖方图，避免极端长条除非用户草稿明确要求。
                    """;
        }
        String p = placement.trim().toUpperCase();
        return switch (p) {
            case "FEED", "FEED_CARD", "NATIVE" -> """
                    版位：信息流 / 原生卡片。
                    建议画幅：方图 1:1、竖版 4:5 或 3:4；宽度与高度需在画面描述中体现为「竖构图/方构图」及安全区（上下预留文案与按钮区，主体居中略偏上）。
                    """;
            case "SPLASH", "SPLASH_SCREEN", "OPENING" -> """
                    版位：开屏全屏。
                    建议画幅：竖屏 9:16，常见像素级约 1242×2688 或 1080×1920；描述中写明竖版全屏、顶部/底部避开系统时间与 CTA 热区，主体视觉集中在中间 60% 区域。
                    """;
            case "BANNER", "TOP_BANNER", "HORIZONTAL" -> """
                    版位：横幅 Banner。
                    建议画幅：强烈横向，常见约 6.4:1、16:3 或 1280×200 量级；描述中强调横向延展构图、主元素沿水平方向分布，上下边距紧凑。
                    """;
            case "VIDEO_COVER", "VIDEO" -> """
                    版位：视频封面 / 短视频首帧。
                    建议画幅：竖版 9:16 或 3:4；描述中适合一屏内强冲击主体，对比清晰，少细碎文字。
                    """;
            default -> """
                    版位代码：%s。
                    请在画面描述中根据该版位常见投放形态自行约定宽高比（竖版/横版/方图），并写出安全区与主体位置建议。
                    """.formatted(p);
        };
    }
}
