package com.example.adagent.creative.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 文生图请求体 */
public class GenerateImageRequest {

    /** 图片描述，建议结构化书写效果更好 */
    @JsonProperty("prompt")
    private String prompt;

    /** 为 true 时写入全局素材目录并落盘图片（须传 userId） */
    @JsonProperty("persist")
    private Boolean persist;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("contentId")
    private String contentId;

    @JsonProperty("placement")
    private String placement;

    /** 素材标题，默认取 prompt 截断 */
    @JsonProperty("title")
    private String title;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Boolean getPersist() {
        return persist;
    }

    public void setPersist(Boolean persist) {
        this.persist = persist;
    }

    public boolean isPersist() {
        return Boolean.TRUE.equals(persist);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getPlacement() {
        return placement;
    }

    public void setPlacement(String placement) {
        this.placement = placement;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
