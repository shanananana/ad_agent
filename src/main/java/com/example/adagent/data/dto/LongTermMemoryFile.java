package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 单用户长期记忆文件结构：memories 为摘要列表，按时间追加。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LongTermMemoryFile {

    private String userId;
    private String updatedAt;
    private List<MemoryEntry> memories = new ArrayList<>();

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<MemoryEntry> getMemories() {
        return memories;
    }

    public void setMemories(List<MemoryEntry> memories) {
        this.memories = memories != null ? memories : new ArrayList<>();
    }

    public static class MemoryEntry {
        private String summary;
        private String at;

        public MemoryEntry() {}

        public MemoryEntry(String summary, String at) {
            this.summary = summary;
            this.at = at;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getAt() {
            return at;
        }

        public void setAt(String at) {
            this.at = at;
        }
    }
}
