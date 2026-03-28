package com.shanananana.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 单会话<strong>聊天记录</strong>持久化结构（{@code data/chat/sessions/{sessionId}.json}）：
 * 含会话元数据与按时间顺序的消息列表，供历史加载与清除。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSessionRecord {

    private String sessionId;
    private String userId;
    private String createdAt;
    private String updatedAt;
    private List<MessageEntry> messages = new ArrayList<>();

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public List<MessageEntry> getMessages() { return messages; }
    public void setMessages(List<MessageEntry> messages) { this.messages = messages != null ? messages : new ArrayList<>(); }

    /** 会话内单条消息：角色与正文，与时间顺序一致；助手消息可带 {@code thinking}（仅展示用，不参与意图上下文）。 */
    public static class MessageEntry {
        private String role;
        private String content;
        /** 助手轮次的思考过程（可选）；旧数据无此字段。 */
        private String thinking;

        public MessageEntry() {}

        public MessageEntry(String role, String content) {
            this(role, content, null);
        }

        public MessageEntry(String role, String content, String thinking) {
            this.role = role;
            this.content = content;
            this.thinking = thinking;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getThinking() { return thinking; }
        public void setThinking(String thinking) { this.thinking = thinking; }
    }
}
