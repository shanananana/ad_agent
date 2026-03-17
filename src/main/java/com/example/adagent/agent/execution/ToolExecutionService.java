package com.example.adagent.agent.execution;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;

@Service
public class ToolExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionService.class);
    private final ChatClient chatClient;

    public ToolExecutionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String executeWithTools(String userInput) {
        logger.info("【工具执行层】执行工具调用，输入长度: {}", userInput != null ? userInput.length() : 0);
        String response = chatClient.prompt()
                .user(userInput)
                .call()
                .content();
        logger.info("【工具执行层】工具调用完成");
        return response;
    }

    public Flux<String> executeWithToolsStream(String userInput) {
        logger.info("【工具执行层】流式执行工具调用");
        return chatClient.prompt()
                .user(userInput)
                .stream()
                .content();
    }
}
