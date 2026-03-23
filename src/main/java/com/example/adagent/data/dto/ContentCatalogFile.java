package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

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
