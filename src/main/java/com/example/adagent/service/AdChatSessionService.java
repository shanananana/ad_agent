package com.example.adagent.service;

import com.example.adagent.agent.AdAgentOrchestrator;
import com.example.adagent.agent.memory.MemoryService;
import com.example.adagent.controller.StreamEvent;
import com.example.adagent.data.ChatHistoryRepository;
import com.example.adagent.data.dto.UserSessionsIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AdChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(AdChatSessionService.class);
    private final AdAgentOrchestrator agentOrchestrator;
    private final MemoryService memoryService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();

    public AdChatSessionService(AdAgentOrchestrator agentOrchestrator, MemoryService memoryService,
                                ChatHistoryRepository chatHistoryRepository) {
        this.agentOrchestrator = agentOrchestrator;
        this.memoryService = memoryService;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    public void bindUserToSession(String sessionId, String userId) {
        if (sessionId != null && userId != null && !userId.isEmpty()) {
            sessionToUserMap.put(sessionId, userId);
        }
    }

    /** @param userId 可选；非空时会在持久化中创建新会话并加入该用户的会话列表 */
    public String createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        memoryService.getShortTermContext(sessionId);
        if (userId != null && !userId.isBlank()) {
            chatHistoryRepository.saveNewSession(sessionId, userId.trim());
        }
        logger.info("【AdChatSessionService】创建会话: {}, userId: {}", sessionId, userId);
        return sessionId;
    }

    public String chat(String sessionId, String userMessage) {
        try {
            String userId = sessionToUserMap.getOrDefault(sessionId, null);
            if (!memoryService.isImmediateLongTermFlush()) {
                memoryService.maybeFlushLongTermMemory(sessionId, userId);
            }
            String response = agentOrchestrator.execute(sessionId, userId, userMessage);
            if (memoryService.isImmediateLongTermFlush()) {
                memoryService.flushRoundToLongTermMemory(sessionId, userId);
            } else {
                memoryService.updateLastActivity(sessionId);
            }
            return response;
        } catch (Exception e) {
            logger.error("AD Agent 处理请求时发生错误", e);
            return "抱歉，处理您的请求时遇到了问题。请稍后再试。";
        }
    }

    public Flux<String> streamChat(String sessionId, String userMessage) {
        String userId = sessionToUserMap.getOrDefault(sessionId, null);
        final String sid = sessionId;
        final String uid = userId;
        if (!memoryService.isImmediateLongTermFlush()) {
            memoryService.maybeFlushLongTermMemory(sid, uid);
        }
        Flux<String> flux = agentOrchestrator.executeStream(sessionId, userId, userMessage);
        return flux.doOnComplete(() -> {
            if (memoryService.isImmediateLongTermFlush()) {
                memoryService.flushRoundToLongTermMemory(sid, uid);
            } else {
                memoryService.updateLastActivity(sid);
            }
        });
    }

    public Flux<StreamEvent> streamChatWithThinking(String sessionId, String userMessage) {
        String userId = sessionToUserMap.getOrDefault(sessionId, null);
        final String sid = sessionId;
        final String uid = userId;
        if (!memoryService.isImmediateLongTermFlush()) {
            memoryService.maybeFlushLongTermMemory(sid, uid);
        }
        return agentOrchestrator.executeStreamWithThinking(sessionId, userId, userMessage)
                .doOnComplete(() -> {
                    if (memoryService.isImmediateLongTermFlush()) {
                        memoryService.flushRoundToLongTermMemory(sid, uid);
                    } else {
                        memoryService.updateLastActivity(sid);
                    }
                });
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        memoryService.ensureSessionLoadedFromStorage(sessionId);
        return memoryService.getShortTermMemoryMessages(sessionId).stream()
                .map(m -> Map.of(
                        "role", m.getRole(),
                        "content", m.getContent()))
                .collect(Collectors.toList());
    }

    public List<UserSessionsIndex.SessionMeta> listSessionsByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return chatHistoryRepository.listSessionsByUser(userId.trim());
    }

    public void clearSession(String sessionId) {
        memoryService.clearShortTermMemory(sessionId);
        sessionToUserMap.remove(sessionId);
    }

    /** 删除会话：持久化与内存均移除 */
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        chatHistoryRepository.deleteSession(sessionId);
        memoryService.clearShortTermMemory(sessionId);
        sessionToUserMap.remove(sessionId);
        logger.info("【AdChatSessionService】删除会话: {}", sessionId);
    }
}
