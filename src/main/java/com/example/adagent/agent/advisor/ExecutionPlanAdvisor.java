package com.example.adagent.agent.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.core.Ordered;

/**
 * 从 {@link ChatClientRequest#context()} 读取本轮规划文本，追加到已有 {@link SystemMessage} 后供模型遵循。
 * <p>通过 {@code chatClient.prompt().advisors(a -> a.param(CONTEXT_KEY, planText))} 传入，按请求隔离。</p>
 */
public class ExecutionPlanAdvisor implements BaseAdvisor {

    /**
     * Advisor 运行时参数键：与 Spring AI 将 {@code .advisors(a -> a.param(...))} 写入的 {@code ChatClientRequest#context()} 一致，值为规划正文 {@link String}。
     */
    public static final String CONTEXT_KEY = "adagent.executionPlanText";

    private final int order;

    public ExecutionPlanAdvisor() {
        this(Ordered.HIGHEST_PRECEDENCE);
    }

    public ExecutionPlanAdvisor(int order) {
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        Object raw = chatClientRequest.context().get(CONTEXT_KEY);
        if (!(raw instanceof String planText) || planText.isBlank()) {
            return chatClientRequest;
        }
        String appendix = "\n\n---\n【本轮推理规划】（请优先按下列建议工具与步骤完成用户请求）\n" + planText.trim();
        var augmented = chatClientRequest.prompt()
                .augmentSystemMessage(sm -> new SystemMessage(sm.getText() + appendix));
        return chatClientRequest.mutate().prompt(augmented).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
