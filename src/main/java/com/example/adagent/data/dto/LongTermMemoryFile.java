package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 单用户<strong>长期记忆</strong> JSON 文件结构（{@code data/long_term_memory/{userId}.json}）：
 * {@code memories} 为按时间追加的偏好/习惯摘要条目列表，供检索注入对话上下文。
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

    /** 一条长期记忆摘要及其写入时间戳。 */
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
