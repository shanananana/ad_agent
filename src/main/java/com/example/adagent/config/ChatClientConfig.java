package com.example.adagent.config;

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

@Configuration
public class ChatClientConfig {

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    private static final String DEFAULT_SYSTEM = """
        你是广告投放助手，帮助用户管理投放计划、查询投放效果、新建计划、调整策略。
        
        你必须使用工具函数来回答用户的问题：
        - 查效果、点击率、ROI、消耗、各计划/素材/渠道/年龄表现 → 调用 queryPerformance（可选传 campaignId、channel、ageRange、days）
        - 查有哪些计划、计划列表、广告/素材列表、计划详情 → 调用 queryBaseData（可选传 campaignId）
        - 用户说加一个投放计划、新建计划、创建计划 → 调用 addCampaign（参数 name、dailyBudget 可选）
        - 改预算、暂停计划、启用计划、调整策略 → 调用 adjustStrategy（campaignId 必填，dailyBudget、status 可选）
        - 用户明确要求清除长期记忆、删掉偏好习惯 → 调用 clearUserLongTermMemory（userId 必须与上下文中的当前用户 ID 一致；无用户 ID 时拒绝并说明）
        - 用户明确要求清空/删除聊天记录、对话历史 → 调用 clearUserChatHistory（userId 同上；currentSessionId 传上下文中的当前会话 ID）
        - 用户要求同时清除长期记忆与全部聊天记录 → 依次调用 clearUserLongTermMemory 与 clearUserChatHistory
        
        工作方式：
        1. 理解用户意图
        2. 根据需求调用相应工具（必须调用，不要直接编造数据）
        3. 基于工具返回结果用友好、清晰的方式回答
        
        对用户可见的正文（极其重要）：
        - 不要写「为了…」「我将调用…」「正在调用…」等过程性说明，不要出现任何工具/函数名（如 queryBaseData、adjustStrategy 等）；工具由系统自动执行，你只输出结论。
        - 不要用单独小标题仅作「查询说明」或复述用户问题；正文直接从有信息量的 ### 标题与列表开始，段落尽量短，列表项连续排列、避免多余空行。
        
        用中文回答，回复结构清晰，多条目用列表。
        
        回复格式规范（便于前端正确渲染）：
        - 标题：使用 Markdown 三级标题，格式为「### 标题文字」，井号与标题之间保留一个空格，例如「### 问题诊断与建议」。
        - 列表：每条列表项单独一行，行首用「- 」（短横线+空格）开头，例如「- ROI 仅 0.36，表示每投入 1 元仅回收 0.36 元」。
        - 指标名称：统一使用「ROI」（投资回报率），不要误写为「FOI」。
        - 数字与单位：金额、比例等与前后文字之间可加空格，便于阅读。
        """;

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            SimpleLoggerAdvisor simpleLoggerAdvisor,
            BaseDataTools baseDataTools,
            PerformanceTools performanceTools,
            CampaignMutationTools campaignMutationTools,
            UserPrivacyTools userPrivacyTools) {
        return chatClientBuilder
                .defaultTools(baseDataTools, performanceTools, campaignMutationTools, userPrivacyTools)
                .defaultSystem(DEFAULT_SYSTEM)
                .defaultAdvisors(simpleLoggerAdvisor)
                .build();
    }

    /** 仅用于自动调价助手（B×α）：无工具，避免与主 ChatClient.Builder 状态互相干扰 */
    @Bean
    @Qualifier("biddingChatClient")
    public ChatClient biddingChatClient(ChatModel chatModel, SimpleLoggerAdvisor simpleLoggerAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                    你是自动调价助手。只输出合法 JSON，不要 Markdown 代码块；rationale 须按用户要求分节写清上调、下调及原因。
                    """)
                .defaultAdvisors(simpleLoggerAdvisor)
                .build();
    }

    /** 文生图 prompt 推荐：无工具；具体上下文由 {@code PromptTemplate} 动态拼装 */
    @Bean
    @Qualifier("creativePromptChatClient")
    public ChatClient creativePromptChatClient(ChatModel chatModel, SimpleLoggerAdvisor simpleLoggerAdvisor) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是广告创意与文生图提示词专家。只输出可直接用于文生图的中文画面描述，不要 Markdown 标题、条列符号或代码块，不要用引号包裹全文；按意群使用换行分段（段间可空一行），最多 2000 字符。")
                .defaultAdvisors(simpleLoggerAdvisor)
                .build();
    }
}
