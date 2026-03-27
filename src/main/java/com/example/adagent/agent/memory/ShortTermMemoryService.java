package com.example.adagent.agent.memory;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <strong>短期记忆</strong>：在进程内按 {@code sessionId} 保存最近若干轮用户与助手消息，
 * 供意图识别（如承接简短确认）与构建对话上下文使用；随会话隔离，不做跨进程持久化。
 */
@Service
public class ShortTermMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(ShortTermMemoryService.class);
    private final Map<String, List<ChatMessage>> sessionMemories = new ConcurrentHashMap<>();
    private static final int DEFAULT_MAX_MESSAGES = 10;

    /** 会话内单条消息：角色（user/assistant 等）与文本内容；助手可带思考过程（仅持久化/展示，不注入意图上下文）。 */
    public static class ChatMessage {
        private final String role;
        private final String content;
        private final String thinking;

        public ChatMessage(String role, String content) {
            this(role, content, null);
        }

        public ChatMessage(String role, String content, String thinking) {
            this.role = role;
            this.content = content;
            this.thinking = thinking;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
        public String getThinking() { return thinking; }
    }

    public List<ChatMessage> getOrCreateMemory(String sessionId) {
        return sessionMemories.computeIfAbsent(sessionId, id -> new ArrayList<>());
    }

    public void addMessage(String sessionId, String role, String content) {
        addMessage(sessionId, role, content, null);
    }

    public void addMessage(String sessionId, String role, String content, String thinking) {
        List<ChatMessage> messages = getOrCreateMemory(sessionId);
        messages.add(new ChatMessage(role, content, thinking));
        while (messages.size() > DEFAULT_MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public List<ChatMessage> getMemory(String sessionId) {
        return sessionMemories.getOrDefault(sessionId, new ArrayList<>());
    }

    /** 从持久化加载时覆盖该会话的消息列表（不触发追加） */
    public void setMessages(String sessionId, List<ChatMessage> messages) {
        if (sessionId == null) return;
        if (messages == null || messages.isEmpty()) {
            sessionMemories.remove(sessionId);
            return;
        }
        sessionMemories.put(sessionId, new ArrayList<>(messages));
    }

    public void clearMemory(String sessionId) {
        sessionMemories.remove(sessionId);
    }

    public String buildContext(String sessionId) {
        List<ChatMessage> messages = getMemory(sessionId);
        if (messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("以下是之前的对话历史：\n\n");
        for (ChatMessage msg : messages) {
            if ("user".equals(msg.getRole())) {
                sb.append("用户：").append(msg.getContent()).append("\n");
            } else if ("assistant".equals(msg.getRole())) {
                sb.append("助手：").append(msg.getContent()).append("\n\n");
            }
        }
        sb.append("---\n\n");
        return sb.toString();
    }
}
