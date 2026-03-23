package com.example.adagent.prompt;

/**
 * <strong>提示词文件路径常量表</strong>：集中声明各场景（主对话、调价、创意、意图识别、长期记忆、版位说明等）对应的
 * {@code classpath:prompts/} 下相对路径，与 {@link ClasspathPromptLoader} 入参约定一致；重命名资源文件时必须同步修改本类。
 */
public final class PromptResourcePaths {

    private PromptResourcePaths() {
    }

    // —— Spring AI ChatClient：default system ——

    /** 主对话 Agent（带工具）：角色、工具说明、对用户正文格式约束 */
    public static final String CHAT_SYSTEM = "chat-system.txt";

    /** 自动调价 B×α：仅 JSON、rationale 分节等 */
    public static final String BIDDING_SYSTEM = "bidding-system.txt";

    /** 文生图「智能生成描述」：短 system，长上下文在用户模板中 */
    public static final String CREATIVE_SYSTEM = "creative-system.txt";

    // —— 意图识别（PromptTemplate）——

    /** 带长期记忆 + 最近对话的意图识别 user 模板 */
    public static final String INTENT_WITH_MEMORY = "intent-with-memory.txt";

    /** 无记忆简化版意图模板（资源保留；当前运行时主路径使用 {@link #INTENT_WITH_MEMORY}） */
    public static final String INTENT_SIMPLE = "intent-simple.txt";

    // —— 创意 / 版位 ——

    /** 智能生成画面描述：拼接版位、高 ROI 素材、内容库、用户草稿 */
    public static final String CREATIVE_SUGGEST_USER = "creative-suggest-user.txt";

    /** 版位 → 画幅与安全区说明（{@link com.example.adagent.creative.CreativePlacementPromptSpec}） */
    public static final class Placement {
        private Placement() {
        }

        public static final String DEFAULT = "placement/default.txt";
        public static final String FEED = "placement/feed.txt";
        public static final String SPLASH = "placement/splash.txt";
        public static final String BANNER = "placement/banner.txt";
        public static final String VIDEO_COVER = "placement/video-cover.txt";
        /** 占位符 {@code {placementCode}} */
        public static final String OTHER = "placement/other.txt";
    }

    // —— 长期记忆 ——

    /** 根据对话摘要判断是否写入长期记忆；占位符 recentMemoriesBlock、conversationSummary */
    public static final String LONG_TERM_MEMORY_JUDGE = "long-term-memory-judge.txt";

    // —— 自动调价 LLM user ——

    /** 调价主 user：planIntroSection、effectWindowEnd、jsonExampleBlock、dataJson */
    public static final String BIDDING_COEFFICIENT_USER = "bidding-coefficient-user.txt";

    /** 调价 JSON 输出示例片段，拼入 {@link #BIDDING_COEFFICIENT_USER} */
    public static final String BIDDING_JSON_EXAMPLE = "bidding-json-example.txt";

    /** 调价 user 中「当前投放计划」头；占位符 campaignId、nameClause */
    public static final String BIDDING_PLAN_INTRO = "bidding/plan-intro.txt";

    // —— 编排器：增强 user 消息 ——

    /** 会话 ID、userId 段、长短期记忆、当前问题 的总骨架 */
    public static final String ORCHESTRATOR_USER_MESSAGE = "orchestrator/user-message.txt";

    /** 已提供 userId 时的工具与隐私调用说明；占位符 userId */
    public static final String ORCHESTRATOR_USER_ID_PROVIDED = "orchestrator/user-id-provided.txt";

    /** 未提供 userId 时的说明（纯文本，无占位符） */
    public static final String ORCHESTRATOR_USER_ID_MISSING = "orchestrator/user-id-missing.txt";

    // —— 规划层（思考过程展示文案，非 LLM 调用）——

    public static final String PLANNING_COT_STEPS = "planning/cot-steps.txt";
    public static final String PLANNING_COT_REASONING = "planning/cot-reasoning.txt";
    public static final String PLANNING_REACT_STEPS = "planning/react-steps.txt";
    /** 占位符 intentType、tools */
    public static final String PLANNING_REACT_REASONING = "planning/react-reasoning.txt";
}
