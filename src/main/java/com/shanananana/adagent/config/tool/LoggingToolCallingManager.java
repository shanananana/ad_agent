package com.shanananana.adagent.config.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;

/**
 * 在 {@link DefaultToolCallingManager} 前后委托：每次工具调用前打印工具名与 JSON 参数，执行后打印返回摘要（INFO）。
 */
public final class LoggingToolCallingManager implements ToolCallingManager {

    private static final Logger logger = LoggerFactory.getLogger(LoggingToolCallingManager.class);
    private static final int RESPONSE_LOG_MAX_CHARS = 2000;

    private final ToolCallingManager delegate;

    public LoggingToolCallingManager(ToolCallingManager delegate) {
        this.delegate = delegate;
    }

    public static LoggingToolCallingManager withDefaultDelegate() {
        return new LoggingToolCallingManager(DefaultToolCallingManager.builder().build());
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        logRequestedToolCalls(chatResponse);
        ToolExecutionResult result = delegate.executeToolCalls(prompt, chatResponse);
        logToolExecutionResults(result);
        return result;
    }

    private static void logRequestedToolCalls(ChatResponse chatResponse) {
        if (chatResponse == null || CollectionUtils.isEmpty(chatResponse.getResults())) {
            return;
        }
        Optional<org.springframework.ai.chat.messages.AssistantMessage> assistant = chatResponse.getResults()
                .stream()
                .filter(g -> g.getOutput() != null && !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
                .map(g -> g.getOutput())
                .findFirst();
        if (assistant.isEmpty()) {
            return;
        }
        for (org.springframework.ai.chat.messages.AssistantMessage.ToolCall toolCall : assistant.get().getToolCalls()) {
            String name = toolCall.name() != null ? toolCall.name() : "";
            String args = toolCall.arguments() != null ? toolCall.arguments() : "";
            logger.info("【工具调用入参】tool={}, arguments={}", name, args);
        }
    }

    private static void logToolExecutionResults(ToolExecutionResult result) {
        if (result == null || CollectionUtils.isEmpty(result.conversationHistory())) {
            return;
        }
        for (Message message : result.conversationHistory()) {
            if (!(message instanceof ToolResponseMessage trm)) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse r : trm.getResponses()) {
                String data = r.responseData();
                String preview = data == null ? ""
                        : (data.length() <= RESPONSE_LOG_MAX_CHARS ? data
                        : data.substring(0, RESPONSE_LOG_MAX_CHARS)
                                + "…(truncated, totalChars=" + data.length() + ")");
                logger.info("【工具调用返回】tool={}, id={}, responsePreview={}", r.name(), r.id(), preview);
            }
        }
    }
}
