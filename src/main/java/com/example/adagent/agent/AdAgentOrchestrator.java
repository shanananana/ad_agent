package com.example.adagent.agent;

import com.example.adagent.agent.execution.ToolExecutionService;
import com.example.adagent.agent.memory.MemoryService;
import com.example.adagent.agent.perception.EntityExtractionService;
import com.example.adagent.agent.perception.IntentRecognitionService;
import com.example.adagent.agent.planning.PlanningService;
import com.example.adagent.controller.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class AdAgentOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AdAgentOrchestrator.class);
    private final IntentRecognitionService intentRecognitionService;
    private final EntityExtractionService entityExtractionService;
    private final PlanningService planningService;
    private final MemoryService memoryService;
    private final ToolExecutionService toolExecutionService;

    public AdAgentOrchestrator(
            IntentRecognitionService intentRecognitionService,
            EntityExtractionService entityExtractionService,
            PlanningService planningService,
            MemoryService memoryService,
            ToolExecutionService toolExecutionService) {
        this.intentRecognitionService = intentRecognitionService;
        this.entityExtractionService = entityExtractionService;
        this.planningService = planningService;
        this.memoryService = memoryService;
        this.toolExecutionService = toolExecutionService;
    }

    public String execute(String sessionId, String userId, String userInput) {
        logger.info("【AD Agent编排器】会话: {}, 用户: {}, 输入: {}", sessionId, userId, userInput);
        try {
            String longTermContext = memoryService.getLongTermContext(userId, userInput);
            String shortTermContext = memoryService.getShortTermContext(sessionId);
            IntentRecognitionService.IntentResult intentResult = intentRecognitionService.recognizeIntent(userInput, longTermContext, shortTermContext);
            EntityExtractionService.EntityResult entityResult = entityExtractionService.extractEntities(userInput);
            logger.info("【AD Agent编排器】感知 - 意图: {}, 实体: {}", intentResult, entityResult);

            if (isAddCampaignClarification(intentResult)) {
                String msg = intentResult.getSuggestedClarificationMessage();
                if (msg != null && !msg.isBlank()) {
                    memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                    memoryService.addToShortTermMemory(sessionId, "assistant", msg, userId);
                    logger.info("【AD Agent编排器】加计划追问，不调用工具");
                    return msg;
                }
            }

            String enhancedPrompt = buildEnhancedPrompt(sessionId, userId, userInput, shortTermContext, longTermContext, intentResult, entityResult);

            boolean needsTool = intentResult.isNeedsTool();
            PlanningService.ExecutionPlan plan = planningService.createPlan(
                    intentResult.getIntentType(), userInput, needsTool);
            logger.info("【AD Agent编排器】规划: {}", plan);

            String response = toolExecutionService.executeWithTools(enhancedPrompt);

            memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
            memoryService.addToShortTermMemory(sessionId, "assistant", response, userId);
            logger.info("【AD Agent编排器】执行完成");
            return response;
        } catch (Exception e) {
            logger.error("【AD Agent编排器】执行失败", e);
            return "抱歉，处理您的请求时遇到了问题。请稍后再试。";
        }
    }

    /** 加计划且需要追问（用户未明确是否拆分，由意图层产出追问话术） */
    private boolean isAddCampaignClarification(IntentRecognitionService.IntentResult intentResult) {
        return "INTENT_ADD_CAMPAIGN".equals(intentResult.getIntentType())
                && intentResult.isNeedClarification()
                && intentResult.getSuggestedClarificationMessage() != null
                && !intentResult.getSuggestedClarificationMessage().isBlank();
    }

    public Flux<String> executeStream(String sessionId, String userId, String userInput) {
        logger.info("【AD Agent编排器】流式 会话: {}, 用户: {}, 输入: {}", sessionId, userId, userInput);
        try {
            String longTermContext = memoryService.getLongTermContext(userId, userInput);
            String shortTermContext = memoryService.getShortTermContext(sessionId);
            IntentRecognitionService.IntentResult intentResult = intentRecognitionService.recognizeIntent(userInput, longTermContext, shortTermContext);
            EntityExtractionService.EntityResult entityResult = entityExtractionService.extractEntities(userInput);
            if (isAddCampaignClarification(intentResult)) {
                String msg = intentResult.getSuggestedClarificationMessage();
                if (msg != null && !msg.isBlank()) {
                    memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                    memoryService.addToShortTermMemory(sessionId, "assistant", msg, userId);
                    return Flux.just(msg);
                }
            }
            String enhancedPrompt = buildEnhancedPrompt(sessionId, userId, userInput, shortTermContext, longTermContext, intentResult, entityResult);
            StringBuilder fullContent = new StringBuilder();
            return toolExecutionService.executeWithToolsStream(enhancedPrompt)
                    .doOnNext(chunk -> { if (chunk != null) fullContent.append(chunk); })
                    .doOnComplete(() -> {
                        memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                        memoryService.addToShortTermMemory(sessionId, "assistant", fullContent.toString(), userId);
                    });
        } catch (Exception e) {
            logger.error("【AD Agent编排器】流式执行失败", e);
            return Flux.just("抱歉，处理您的请求时遇到了问题。请稍后再试。");
        }
    }

    /**
     * 流式执行并产出「思考过程」事件 + 内容流，便于前端展示。
     */
    public Flux<StreamEvent> executeStreamWithThinking(String sessionId, String userId, String userInput) {
        logger.info("【AD Agent编排器】流式(含思考) 会话: {}, 用户: {}, 输入: {}", sessionId, userId, userInput);
        try {
            String longTermContext = memoryService.getLongTermContext(userId, userInput);
            String shortTermContext = memoryService.getShortTermContext(sessionId);
            IntentRecognitionService.IntentResult intentResult = intentRecognitionService.recognizeIntent(userInput, longTermContext, shortTermContext);
            EntityExtractionService.EntityResult entityResult = entityExtractionService.extractEntities(userInput);
            if (isAddCampaignClarification(intentResult)) {
                String msg = intentResult.getSuggestedClarificationMessage();
                if (msg != null && !msg.isBlank()) {
                    memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                    memoryService.addToShortTermMemory(sessionId, "assistant", msg, userId);
                    return Flux.just(StreamEvent.content(msg));
                }
            }
            String enhancedPrompt = buildEnhancedPrompt(sessionId, userId, userInput, shortTermContext, longTermContext, intentResult, entityResult);
            boolean needsTool = intentResult.isNeedsTool();
            PlanningService.ExecutionPlan plan = planningService.createPlan(
                    intentResult.getIntentType(), userInput, needsTool);

            StringBuilder thinking = new StringBuilder();
            thinking.append("意图: ").append(intentResult.getIntentType()).append("\n");
            thinking.append("需要工具: ").append(needsTool).append("\n");
            thinking.append("规划: ").append(plan.getPlanType());
            if (!plan.getRequiredTools().isEmpty()) {
                thinking.append("，工具: ").append(String.join(", ", plan.getRequiredTools()));
            }
            thinking.append("\n").append(plan.getReasoning());

            Flux<StreamEvent> thinkingEvent = Flux.just(StreamEvent.thinking(thinking.toString()));
            AtomicReference<StringBuilder> fullContent = new AtomicReference<>(new StringBuilder());
            Flux<StreamEvent> contentEvents = toolExecutionService.executeWithToolsStream(enhancedPrompt)
                    .doOnNext(chunk -> {
                        if (chunk != null) {
                            fullContent.get().append(chunk);
                        }
                    })
                    .map(StreamEvent::content)
                    .doOnComplete(() -> {
                        String reply = fullContent.get().toString();
                        memoryService.addToShortTermMemory(sessionId, "user", userInput, userId);
                        memoryService.addToShortTermMemory(sessionId, "assistant", reply, userId);
                    });
            return thinkingEvent.concatWith(contentEvents);
        } catch (Exception e) {
            logger.error("【AD Agent编排器】流式(含思考)执行失败", e);
            return Flux.just(StreamEvent.thinking("执行异常: " + e.getMessage()),
                    StreamEvent.content("抱歉，处理您的请求时遇到了问题。请稍后再试。"));
        }
    }

    private String buildEnhancedPrompt(String sessionId, String userId, String userInput, String shortTermContext, String longTermContext,
                                       IntentRecognitionService.IntentResult intentResult,
                                       EntityExtractionService.EntityResult entityResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【当前会话ID】").append(sessionId).append("。\n\n");
        if (userId != null && !userId.isBlank()) {
            prompt.append("【当前用户ID】").append(userId).append("。调用 queryBaseData、queryPerformance、addCampaign、adjustStrategy 时请传入参数 userId 为该值，以按用户隔离基础数据与效果数据。\n\n");
        }
        if (longTermContext != null && !longTermContext.isEmpty()) {
            prompt.append(longTermContext);
        }
        if (!shortTermContext.isEmpty()) {
            prompt.append(shortTermContext);
        }
        prompt.append("当前用户问题：").append(userInput);
        return prompt.toString();
    }
}
