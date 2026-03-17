package com.example.adagent.agent.memory;

import com.example.adagent.data.ChatHistoryRepository;
import com.example.adagent.data.dto.ChatSessionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一记忆服务：短期记忆（会话对话）+ 长期记忆（按用户分文件）+ 聊天记录持久化。
 * 长期记忆写入时机由配置 ad-agent.memory.immediate-long-term-flush 控制。
 */
@Service
public class MemoryService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);
    private static final long IDLE_THRESHOLD_MS = 5 * 60 * 1000; // 5 分钟

    @Value("${ad-agent.memory.immediate-long-term-flush:true}")
    private boolean immediateLongTermFlush;

    private final ShortTermMemoryService shortTermMemoryService;
    private final LongTermMemoryService longTermMemoryService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ConcurrentHashMap<String, Long> lastActivityBySession = new ConcurrentHashMap<>();

    public MemoryService(ShortTermMemoryService shortTermMemoryService,
                         LongTermMemoryService longTermMemoryService,
                         ChatHistoryRepository chatHistoryRepository) {
        this.shortTermMemoryService = shortTermMemoryService;
        this.longTermMemoryService = longTermMemoryService;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    public boolean isImmediateLongTermFlush() {
        return immediateLongTermFlush;
    }

    public String getShortTermContext(String sessionId) {
        return shortTermMemoryService.buildContext(sessionId);
    }

    public void addToShortTermMemory(String sessionId, String role, String content) {
        shortTermMemoryService.addMessage(sessionId, role, content);
    }

    /** 追加消息并可选持久化（有 userId 时写入聊天记录文件） */
    public void addToShortTermMemory(String sessionId, String role, String content, String userId) {
        shortTermMemoryService.addMessage(sessionId, role, content);
        if (userId != null && !userId.isBlank()) {
            chatHistoryRepository.appendMessage(sessionId, role, content);
        }
    }

    public List<ShortTermMemoryService.ChatMessage> getShortTermMemoryMessages(String sessionId) {
        return shortTermMemoryService.getMemory(sessionId);
    }

    public void clearShortTermMemory(String sessionId) {
        shortTermMemoryService.clearMemory(sessionId);
        lastActivityBySession.remove(sessionId);
    }

    /** 若内存中无该会话记录，则从持久化加载并填入短期记忆（刷新页面后加载历史用） */
    public void ensureSessionLoadedFromStorage(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (!shortTermMemoryService.getMemory(sessionId).isEmpty()) return;
        ChatSessionRecord record = chatHistoryRepository.loadSession(sessionId);
        if (record == null || record.getMessages() == null) return;
        List<ShortTermMemoryService.ChatMessage> list = record.getMessages().stream()
                .map(m -> new ShortTermMemoryService.ChatMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
        shortTermMemoryService.setMessages(sessionId, list);
        logger.debug("【记忆】从存储加载会话 {} 共 {} 条消息", sessionId, list.size());
    }

    /** 获取长期记忆上下文字符串（供编排器注入 prompt） */
    public String getLongTermContext(String userId, String query) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        List<LongTermMemoryService.MemorySummary> list = longTermMemoryService.retrieveRelevantMemories(userId, query, 5);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("【该用户的历史偏好/习惯】\n");
        for (LongTermMemoryService.MemorySummary m : list) {
            sb.append("- ").append(m.getSummary()).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** 更新会话最后活动时间（非立即模式时，每轮结束调用） */
    public void updateLastActivity(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            lastActivityBySession.put(sessionId, System.currentTimeMillis());
        }
    }

    /**
     * 非立即模式时，在用户发送新消息时调用：若距上次活动已超过 5 分钟，则把短期记忆汇总并写入长期记忆；否则仅更新活动时间。
     */
    public void maybeFlushLongTermMemory(String sessionId, String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastActivityBySession.getOrDefault(sessionId, 0L);
        if (last > 0 && (now - last) < IDLE_THRESHOLD_MS) {
            updateLastActivity(sessionId);
            return;
        }
        doFlushToLongTerm(sessionId, userId);
        updateLastActivity(sessionId);
    }

    /**
     * 立即模式时，每轮对话结束后调用：将当前短期记忆汇总并写入长期记忆。
     */
    public void flushRoundToLongTermMemory(String sessionId, String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        doFlushToLongTerm(sessionId, userId);
    }

    private void doFlushToLongTerm(String sessionId, String userId) {
        List<ShortTermMemoryService.ChatMessage> messages = shortTermMemoryService.getMemory(sessionId);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        StringBuilder summary = new StringBuilder();
        for (ShortTermMemoryService.ChatMessage m : messages) {
            summary.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        if (summary.length() > 5000) {
            summary.setLength(5000);
            summary.append("…");
        }
        try {
            longTermMemoryService.saveSummaryFromChat(userId, sessionId, summary.toString());
        } catch (Exception e) {
            logger.warn("【记忆】写入长期记忆失败: {}", e.getMessage());
        }
    }
}
