package com.shanananana.adagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动时打印对话 / 文生图配置摘要。
 */
@Component
public class LlmProviderStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderStartupLogger.class);

    private final Environment environment;
    private final ChatModel chatModel;

    public LlmProviderStartupLogger(Environment environment, ChatModel chatModel) {
        this.environment = environment;
        this.chatModel = chatModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String baseUrl = environment.getProperty("spring.ai.openai.base-url", "(未配置)");
        String model = environment.getProperty("spring.ai.openai.chat.options.model", "(未配置)");
        String imageProvider = environment.getProperty("spring.ai.model.image", "none");

        log.info(
                "【LLM】对话 OpenAI 兼容 base-url={} model={} 实现={}",
                baseUrl,
                model,
                chatModel.getClass().getSimpleName());
        log.info("【LLM】文生图 provider={}", imageProvider);
    }
}
