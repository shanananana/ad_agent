package com.shanananana.adagent.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将 Markdown 技能对应的 {@link ToolCallback} 追加到 {@link ChatClient.Builder#defaultToolCallbacks}，并在注册时打完整清单日志。
 */
public final class DefaultMarkdownSkillsChatClientSupport implements MarkdownSkillsChatClientSupport {

    private static final Logger log = LoggerFactory.getLogger(DefaultMarkdownSkillsChatClientSupport.class);

    private final List<ToolCallback> toolCallbacks;
    private final List<SkillSummary> skillSummaries;

    public DefaultMarkdownSkillsChatClientSupport(MarkdownSkillRegistry registry) {
        this.toolCallbacks = registry.getToolCallbacks();
        this.skillSummaries = registry.getSummaries();
    }

    @Override
    public void applyTo(ChatClient.Builder builder) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            log.warn("【Markdown技能】无 skill 工具可注册到 ChatClient（请检查 spring.ai.markdown-skills.locations）");
            return;
        }
        String names = skillSummaries.stream()
                .map(s -> s.toolName() + "(id=" + s.id() + ")")
                .collect(Collectors.joining(", "));
        log.info("【Markdown技能】向 ChatClient 合并注册 {} 个 skill 工具: [{}]", skillSummaries.size(), names);
        builder.defaultToolCallbacks(toolCallbacks);
    }
}
