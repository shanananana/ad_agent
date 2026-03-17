package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/** 单会话聊天记录（持久化） */
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

    public static class MessageEntry {
        private String role;
        private String content;
        public MessageEntry() {}
        public MessageEntry(String role, String content) { this.role = role; this.content = content; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
