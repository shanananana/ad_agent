package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户维度<strong>推广内容库</strong> JSON 顶层（{@code contents.json}）：版本元数据与 {@link ContentItem} 列表，
 * 供素材生成时拼入「推广内容」摘要。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentCatalogFile {

    private String version = "1.0";
    private String updatedAt;
    private List<ContentItem> contents = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ContentItem> getContents() {
        return contents;
    }

    public void setContents(List<ContentItem> contents) {
        this.contents = contents != null ? contents : new ArrayList<>();
    }
}
