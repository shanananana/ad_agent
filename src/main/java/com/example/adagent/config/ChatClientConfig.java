package com.example.adagent.config;

import com.example.adagent.tools.BaseDataTools;
import com.example.adagent.tools.CampaignMutationTools;
import com.example.adagent.tools.PerformanceTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String DEFAULT_SYSTEM = """
        你是广告投放助手，帮助用户管理投放计划、查询投放效果、新建计划、调整策略。
        
        你必须使用工具函数来回答用户的问题：
        - 查效果、点击率、ROI、消耗、各计划/素材/渠道/年龄表现 → 调用 queryPerformance（可选传 campaignId、channel、ageRange、days）
        - 查有哪些计划、计划列表、广告/素材列表、计划详情 → 调用 queryBaseData（可选传 campaignId）
        - 用户说加一个投放计划、新建计划、创建计划 → 调用 addCampaign（参数 name、dailyBudget 可选）
        - 改预算、暂停计划、启用计划、调整策略 → 调用 adjustStrategy（campaignId 必填，dailyBudget、status 可选）
        
        工作方式：
        1. 理解用户意图
        2. 根据需求调用相应工具（必须调用，不要直接编造数据）
        3. 基于工具返回结果用友好、清晰的方式回答
        
        用中文回答，回复结构清晰，多条目用列表。
        """;

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            BaseDataTools baseDataTools,
            PerformanceTools performanceTools,
            CampaignMutationTools campaignMutationTools) {
        return chatClientBuilder
                .defaultTools(baseDataTools, performanceTools, campaignMutationTools)
                .defaultSystem(DEFAULT_SYSTEM)
                .build();
    }
}
