package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/** 某用户的会话列表索引 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSessionsIndex {

    private String userId;
    private String updatedAt;
    private List<SessionMeta> sessions = new ArrayList<>();

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public List<SessionMeta> getSessions() { return sessions; }
    public void setSessions(List<SessionMeta> sessions) { this.sessions = sessions != null ? sessions : new ArrayList<>(); }

    public static class SessionMeta {
        private String sessionId;
        private String createdAt;
        private String updatedAt;
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    }
}
