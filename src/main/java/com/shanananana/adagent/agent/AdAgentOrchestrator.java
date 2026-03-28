package com.shanananana.adagent.agent;

import com.shanananana.adagent.agent.execution.ToolExecutionService;
import com.shanananana.adagent.agent.memory.MemoryService;
import com.shanananana.adagent.agent.perception.EntityExtractionService;
import com.shanananana.adagent.agent.perception.IntentRecognitionService;
import com.shanananana.adagent.agent.planning.PlanningService;
import com.shanananana.adagent.controller.StreamEvent;
import com.shanananana.adagent.data.LongTermMemoryRepository;
import com.shanananana.adagent.prompt.ClasspathPromptLoader;
import com.shanananana.adagent.prompt.PromptResourcePaths;
import com.shanananana.adagent.service.AdChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 对话式投放 Agent 编排核心：单轮内串联<strong>意图识别 → 实体抽取 → 规划（CoT / ReAct）→ 工具执行</strong>，
 * 并读写短期/长期记忆与聊天历史。
 * <p>对<strong>隐私清除</strong>类意图在服务端同步删除磁盘文件后短路返回，并可建议前端刷新。
 * 对外仅暴露 {@link #executeStreamWithThinking}，供 {@code chat.html} SSE 使用。</p>
 */
@Service
public class AdAgentOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AdAgentOrchestrator.class);
    private final IntentRecognitionService intentRecognitionService;
    private final EntityExtractionService entityExtractionService;
    private final PlanningService planningService;
    private final MemoryService memoryService;
    private final ToolExecutionService toolExecutionService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final AdChatSessionService adChatSessionService;
    private final ClasspathPromptLoader classpathPromptLoader;

    public AdAgentOrchestrator(
            IntentRecognitionService intentRecognitionService,
            EntityExtractionService entityExtractionService,
            PlanningService planningService,
            MemoryService memoryService,
            ToolExecutionService toolExecutionService,
            LongTermMemoryRepository longTermMemoryRepository,
            ClasspathPromptLoader classpathPromptLoader,
            @Lazy AdChatSessionService adChatSessionService) {
        this.intentRecognitionService = intentRecognitionService;
        this.entityExtractionService = entityExtractionService;
        this.planningService = planningService;
        this.memoryService = memoryService;
        this.toolExecutionService = toolExecutionService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.classpathPromptLoader = classpathPromptLoader;
        this.adChatSessionService = adChatSessionService;
    }

    /**
     * 并行加载长期 / 短期记忆上下文，缩短首包前耗时。
     */
    private MemoryContexts loadMemoryContextsParallel(String sessionId, String userId, String userInput) {
        CompletableFuture<String> longTerm = CompletableFuture.supplyAsync(
                () -> memoryService.getLongTermContext(userId, userInput));
        CompletableFuture<String> shortTerm = CompletableFuture.supplyAsync(
                () -> memoryService.getShortTermContext(sessionId));
        CompletableFuture.allOf(longTerm, shortTerm).join();
        return new MemoryContexts(longTerm.join(), shortTerm.join());
    }

    private record MemoryContexts(String longTermContext, String shortTermContext) {}

    /**
     * 流式执行并产出「思考过程」事件 + 内容流，便于前端展示。
     */
    public Flux<StreamEvent> executeStreamWithThinking(String sessionId, String userId, String userInput) {
        logger.info("【AD Agent编排器】流式(含思考) 会话: {}, 用户: {}, 输入: {}", sessionId, userId, userInput);
        try {
            MemoryContexts ctx = loadMemoryContextsParallel(sessionId, userId, userInput);
            IntentRecognitionService.IntentResult intentResult =
                    intentRecognitionService.recognizeIntent(userInput, ctx.longTermContext(), ctx.shortTermContext());
            EntityExtractionService.EntityResult entityResult = entityExtractionService.extractEntities(userInput);
            logger.info("【AD Agent编排器】感知 - 意图: {}, 实体: {}", intentResult, entityResult);
            if (isAddCampaignClarification(intentResult)) {
                String msg = intentResult.getSuggestedClarificationMessage();
                if (msg != null && !msg.isBlank()) {
                    String clarifyThinking = "意图: INTENT_ADD_CAMPAIGN\n说明: 需先向用户澄清投放计划拆分方式（未走工具链路）。";
                    memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                    memoryService.addToShortTermMemory(sessionId, "assistant", msg, userId, clarifyThinking);
                    return Flux.just(StreamEvent.thinking(clarifyThinking), StreamEvent.content(msg));
                }
            }
            PrivacyClearHandled privacy = runServerSidePrivacyClearIfApplicable(intentResult, userId, sessionId);
            if (privacy != null) {
                memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                String thinking = "意图: " + intentResult.getIntentType() + "\n服务端已执行隐私数据删除（不依赖模型调工具）。";
                memoryService.addToShortTermMemory(sessionId, "assistant", privacy.message(), userId, thinking);
                if (privacy.suggestPageRefresh()) {
                    return Flux.just(
                            StreamEvent.thinking(thinking),
                            StreamEvent.content(privacy.message()),
                            StreamEvent.client("{\"reload\":true}"));
                }
                return Flux.just(StreamEvent.thinking(thinking), StreamEvent.content(privacy.message()));
            }
            String enhancedPrompt = buildEnhancedPrompt(sessionId, userId, userInput, ctx.shortTermContext(),
                    ctx.longTermContext(), intentResult, entityResult);
            boolean needsTool = intentResult.isNeedsTool();
            PlanningService.ExecutionPlan plan = planningService.createPlan(
                    intentResult.getIntentType(), userInput, needsTool);

            StringBuilder planningThinking = new StringBuilder();
            planningThinking.append("意图: ").append(intentResult.getIntentType()).append("\n");
            planningThinking.append("需要工具: ").append(needsTool).append("\n");
            planningThinking.append("规划: ").append(plan.getPlanType());
            if (!plan.getRequiredTools().isEmpty()) {
                planningThinking.append("，工具: ").append(String.join(", ", plan.getRequiredTools()));
            }
            planningThinking.append("\n").append(plan.getReasoning());

            StringBuilder allThinking = new StringBuilder(planningThinking.toString());
            Flux<StreamEvent> thinkingEvent = Flux.just(StreamEvent.thinking(planningThinking.toString()));
            AtomicReference<StringBuilder> fullContent = new AtomicReference<>(new StringBuilder());
            Flux<StreamEvent> modelThenContent = Flux.create((FluxSink<StreamEvent> sink) -> {
                var subscription = toolExecutionService.executeWithToolsStreamForUi(enhancedPrompt, plan, dropped -> {
                    if (dropped != null && !dropped.isBlank()) {
                        String piece = "模型侧过程（已从正文剥离）：\n" + dropped;
                        allThinking.append('\n').append(piece);
                        sink.next(StreamEvent.thinking(piece));
                    }
                }).subscribe(
                        chunk -> {
                            if (chunk != null) {
                                fullContent.get().append(chunk);
                                sink.next(StreamEvent.content(chunk));
                            }
                        },
                        sink::error,
                        () -> {
                            String reply = fullContent.get().toString();
                            memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                            memoryService.addToShortTermMemory(sessionId, "assistant", reply, userId, allThinking.toString().trim());
                            sink.complete();
                        });
                sink.onDispose(subscription::dispose);
            }, FluxSink.OverflowStrategy.BUFFER);
            return thinkingEvent.concatWith(modelThenContent);
        } catch (Exception e) {
            logger.error("【AD Agent编排器】流式(含思考)执行失败", e);
            return Flux.just(StreamEvent.thinking("执行异常: " + e.getMessage()),
                    StreamEvent.content("抱歉，处理您的请求时遇到了问题。请稍后再试。"));
        }
    }

    /** 加计划且需要追问（用户未明确是否拆分，由意图层产出追问话术） */
    private boolean isAddCampaignClarification(IntentRecognitionService.IntentResult intentResult) {
        return "INTENT_ADD_CAMPAIGN".equals(intentResult.getIntentType())
                && intentResult.isNeedClarification()
                && intentResult.getSuggestedClarificationMessage() != null
                && !intentResult.getSuggestedClarificationMessage().isBlank();
    }

    private record PrivacyClearHandled(String message, boolean suggestPageRefresh) {}

    /**
     * 隐私类意图在服务端直接删文件，避免仅依赖 LLM 是否调用工具导致「口头已删、磁盘未删」。
     *
     * @return 若本分支已处理则非 null；否则 null 走常规模型+工具链路。成功删除磁盘数据时 suggestPageRefresh=true，便于前端整页刷新。
     */
    private PrivacyClearHandled runServerSidePrivacyClearIfApplicable(IntentRecognitionService.IntentResult intentResult,
                                                                    String userId, String sessionId) {
        if (!intentResult.isNeedsTool()) {
            return null;
        }
        String type = intentResult.getIntentType();
        boolean clearLt = "INTENT_CLEAR_LONG_TERM_MEMORY".equals(type) || "INTENT_CLEAR_ALL_USER_MEMORY".equals(type);
        boolean clearChat = "INTENT_CLEAR_CHAT_HISTORY".equals(type) || "INTENT_CLEAR_ALL_USER_MEMORY".equals(type);
        if (!clearLt && !clearChat) {
            return null;
        }
        if (userId == null || userId.isBlank()) {
            return new PrivacyClearHandled(
                    "当前请求未携带用户标识，无法在服务端清除您的长期记忆或聊天记录。请先登录或在请求中传入 userId 后再试。",
                    false);
        }
        String uid = userId.trim();
        try {
            if (clearLt) {
                longTermMemoryRepository.deleteForUser(uid);
            }
            if (clearChat) {
                adChatSessionService.deleteAllChatHistoryForUser(uid, sessionId);
            }
        } catch (Exception e) {
            logger.error("【AD Agent编排器】服务端隐私清除失败 userId={}", uid, e);
            return new PrivacyClearHandled("清除数据时发生错误，请稍后重试。若问题持续，请联系管理员。", false);
        }
        if (clearLt && clearChat) {
            return new PrivacyClearHandled("已在服务端清除您的长期记忆与全部聊天记录（含磁盘上的会话文件）。", true);
        }
        if (clearLt) {
            return new PrivacyClearHandled("已在服务端清除您的长期记忆（偏好与习惯摘要文件已删除）。", true);
        }
        return new PrivacyClearHandled(
                "已在服务端删除您的全部聊天记录（含索引与 sessions 目录下属于您的会话文件），当前会话上下文已清空。",
                true);
    }

    private String buildEnhancedPrompt(String sessionId, String userId, String userInput, String shortTermContext, String longTermContext,
                                       IntentRecognitionService.IntentResult intentResult,
                                       EntityExtractionService.EntityResult entityResult) {
        String userIdBlock = (userId != null && !userId.isBlank())
                ? classpathPromptLoader.renderTemplate(
                        PromptResourcePaths.ORCHESTRATOR_USER_ID_PROVIDED,
                        Map.of("userId", userId.trim()))
                : classpathPromptLoader.loadText(PromptResourcePaths.ORCHESTRATOR_USER_ID_MISSING);
        Map<String, Object> model = new HashMap<>();
        model.put("sessionId", sessionId != null ? sessionId : "");
        model.put("userIdBlock", userIdBlock);
        model.put("longTermContext", (longTermContext != null && !longTermContext.isEmpty()) ? longTermContext : "");
        model.put("shortTermContext", (shortTermContext != null && !shortTermContext.isEmpty()) ? shortTermContext : "");
        model.put("userInput", userInput != null ? userInput : "");
        return classpathPromptLoader.renderTemplate(PromptResourcePaths.ORCHESTRATOR_USER_MESSAGE, model);
    }
}
