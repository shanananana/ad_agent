package com.example.adagent.config;

import com.example.adagent.prompt.ClasspathPromptLoader;
import com.example.adagent.prompt.PromptResourcePaths;
import com.example.adagent.agent.advisor.ExecutionPlanAdvisor;
import com.example.adagent.tools.BaseDataTools;
import com.example.adagent.tools.CampaignMutationTools;
import com.example.adagent.tools.PerformanceTools;
import com.example.adagent.tools.UserPrivacyTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring AI {@link ChatClient} 的 Bean 定义：<strong>主对话 Client</strong> 注册查询/变更/隐私等工具并注入默认 system 提示；
 * <strong>{@code biddingChatClient}</strong> 无工具、专供自动调价 LLM，避免与主 Builder 共享状态导致串扰。
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    /**
     * 将本轮 {@link com.example.adagent.agent.planning.PlanningService.ExecutionPlan} 格式化后经 {@code .advisors(param)} 注入 system。
     * 顺序早于 {@link SimpleLoggerAdvisor}，便于日志中看到已追加规划后的请求。
     */
    @Bean
    public ExecutionPlanAdvisor executionPlanAdvisor() {
        return new ExecutionPlanAdvisor(Ordered.HIGHEST_PRECEDENCE);
    }

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            ExecutionPlanAdvisor executionPlanAdvisor,
            SimpleLoggerAdvisor simpleLoggerAdvisor,
            ClasspathPromptLoader classpathPromptLoader,
            BaseDataTools baseDataTools,
            PerformanceTools performanceTools,
            CampaignMutationTools campaignMutationTools,
            UserPrivacyTools userPrivacyTools) {
        return chatClientBuilder
                .defaultTools(baseDataTools, performanceTools, campaignMutationTools, userPrivacyTools)
                .defaultSystem(classpathPromptLoader.loadText(PromptResourcePaths.CHAT_SYSTEM))
                .defaultAdvisors(executionPlanAdvisor, simpleLoggerAdvisor)
                .build();
    }

    /** 仅用于自动调价助手（B×α）：无工具，避免与主 ChatClient.Builder 状态互相干扰 */
    @Bean
    @Qualifier("biddingChatClient")
    public ChatClient biddingChatClient(
            ChatModel chatModel,
            SimpleLoggerAdvisor simpleLoggerAdvisor,
            ClasspathPromptLoader classpathPromptLoader) {
        return ChatClient.builder(chatModel)
                .defaultSystem(classpathPromptLoader.loadText(PromptResourcePaths.BIDDING_SYSTEM))
                .defaultAdvisors(simpleLoggerAdvisor)
                .build();
    }

    /** 文生图 prompt 推荐：无工具；具体上下文由 {@code PromptTemplate} 动态拼装 */
    @Bean
    @Qualifier("creativePromptChatClient")
    public ChatClient creativePromptChatClient(
            ChatModel chatModel,
            SimpleLoggerAdvisor simpleLoggerAdvisor,
            ClasspathPromptLoader classpathPromptLoader) {
        return ChatClient.builder(chatModel)
                .defaultSystem(classpathPromptLoader.loadText(PromptResourcePaths.CREATIVE_SYSTEM))
                .defaultAdvisors(simpleLoggerAdvisor)
                .build();
    }
}
