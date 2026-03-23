package com.example.adagent.agent.execution;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型回复正文<strong>净化</strong>工具类：在流式与非流式场景下剥离「为了…将调用某工具」类冗长开场白、
 * 重复的小标题等，使用户可见正文与「思考过程」通道展示的内容解耦。
 * <p>无状态，仅提供静态方法；流式场景通过 {@link PrefixStrip} 返回已剥离片段与剩余缓冲。</p>
 */
public final class ReplySanitizer {

    private static final Pattern LEADING_FOR_TOOL =
            Pattern.compile("^\\s*为了[\\s\\S]{1,2000}?将调用[\\s\\S]{1,800}?[。.]\\s*");
    private static final Pattern REDUNDANT_HEADING =
            Pattern.compile("(?m)^###\\s*计划详情查询\\s*\\n+");

    private ReplySanitizer() {
    }

    /**
     * 若缓冲区已包含完整一句「为了…将调用…。」则从开头剥离；否则原样返回。
     */
    public static PrefixStrip stripToolNarrationPrefixIfComplete(String buffer) {
        if (buffer == null || buffer.isEmpty()) {
            return new PrefixStrip("", buffer);
        }
        Matcher m = LEADING_FOR_TOOL.matcher(buffer);
        if (m.find() && m.start() == 0) {
            String removed = m.group();
            return new PrefixStrip(removed, buffer.substring(m.end()));
        }
        String remainder = REDUNDANT_HEADING.matcher(buffer).replaceFirst("");
        return new PrefixStrip("", remainder);
    }

    /**
     * 正文起始：第一个 Markdown 三级标题行（### ），用于跳过已剥离开场白后仍存在的空行。
     */
    public static int indexOfFirstMarkdownHeading(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        Matcher m = Pattern.compile("(?m)^###\\s+\\S").matcher(s);
        return m.find() ? m.start() : -1;
    }

    /** 非流式整段回复：与 UI 流式门控最终效果对齐。 */
    public static String sanitizeFullReply(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        PrefixStrip ps = stripToolNarrationPrefixIfComplete(raw);
        String work = ps.remainder();
        int h = indexOfFirstMarkdownHeading(work);
        return h >= 0 ? work.substring(h) : work;
    }

    /**
     * 一次前缀剥离的结果：已从缓冲区头部去掉的文本，以及供后续继续输出或拼接的剩余正文。
     */
    public record PrefixStrip(String removed, String remainder) {
    }
}
