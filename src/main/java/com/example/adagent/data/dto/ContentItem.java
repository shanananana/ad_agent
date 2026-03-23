package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * <strong>内容库</strong>单条推广内容：名称与摘要等供「智能生成描述」拼入 LLM 上下文；{@code id} 为 UUID。
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
