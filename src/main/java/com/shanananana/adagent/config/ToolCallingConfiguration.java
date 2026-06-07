package com.shanananana.adagent.config;

import com.shanananana.adagent.config.tool.LoggingToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 注册带日志的 {@link ToolCallingManager}，供支持 function calling 的 {@link org.springframework.ai.chat.model.ChatModel} 注入，
 * 在每次工具执行前打印工具名与参数（见 {@link LoggingToolCallingManager}）。
 */
@Configuration
public class ToolCallingConfiguration {

    @Bean
    @Primary
    public ToolCallingManager toolCallingManager() {
        return LoggingToolCallingManager.withDefaultDelegate();
    }
}
