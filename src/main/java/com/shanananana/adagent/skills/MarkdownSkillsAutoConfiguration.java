package com.shanananana.adagent.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import java.util.List;

/**
 * Markdown 技能：扫描、注册为 {@link ToolCallback}，并通过 {@link ChatClientCustomizer} 合并进主 {@link ChatClient}。
 */
@AutoConfiguration(after = ChatClientAutoConfiguration.class)
@ConditionalOnClass({ChatClient.class, ToolCallback.class})
@EnableConfigurationProperties(MarkdownSkillsProperties.class)
public class MarkdownSkillsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MarkdownSkillsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MarkdownSkillRegistry markdownSkillRegistry(
            MarkdownSkillsProperties properties,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        if (!properties.isEnabled()) {
            log.info("spring.ai.markdown-skills.enabled=false，未加载 Markdown 技能");
            return new MarkdownSkillRegistry(List.of(), List.of());
        }
        var parser = new MarkdownSkillParser(objectMapper);
        var loader = new MarkdownSkillLoader();
        var skills = loader.loadAll(properties, parser, resourceLoader);
        var registry = new MarkdownSkillToolFactory(objectMapper).buildRegistry(skills, properties.getMaxBodyChars());
        for (var s : registry.getSummaries()) {
            log.info("已加载 Markdown 技能 id={} toolName={} source={}", s.id(), s.toolName(), s.source());
        }
        if (registry.getSummaries().isEmpty()) {
            log.warn("未加载任何 Markdown 技能，请检查 spring.ai.markdown-skills.locations");
        }
        return registry;
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.markdown-skills", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MarkdownSkillsChatClientSupport markdownSkillsChatClientSupport(MarkdownSkillRegistry registry) {
        return new DefaultMarkdownSkillsChatClientSupport(registry);
    }

    @Bean
    @ConditionalOnBean(MarkdownSkillsChatClientSupport.class)
    public ChatClientCustomizer markdownSkillsChatClientCustomizer(MarkdownSkillsChatClientSupport support) {
        return support::applyTo;
    }
}
