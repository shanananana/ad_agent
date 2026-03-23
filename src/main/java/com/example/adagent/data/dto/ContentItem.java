package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 全局内容基础信息，参与拼 prompt 等；{@code id} 为 UUID。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentItem {

    private String id;
    private String name;
    private String summary;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
