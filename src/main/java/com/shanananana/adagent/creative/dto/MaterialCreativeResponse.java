package com.shanananana.adagent.creative.dto;

/**
 * 素材 Tab「技能一键」响应：模型正文 + 从工具返回 JSON 解析的字段（便于本页预览与绑定）。
 */
public class MaterialCreativeResponse {

    private String status;
    private String message;
    private String assistantReply;
    private String imageUrl;
    private String creativeId;
    private String persistedPath;

    public static MaterialCreativeResponse error(String message) {
        MaterialCreativeResponse r = new MaterialCreativeResponse();
        r.setStatus("error");
        r.setMessage(message != null ? message : "未知错误");
        return r;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAssistantReply() {
        return assistantReply;
    }

    public void setAssistantReply(String assistantReply) {
        this.assistantReply = assistantReply;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getCreativeId() {
        return creativeId;
    }

    public void setCreativeId(String creativeId) {
        this.creativeId = creativeId;
    }

    public String getPersistedPath() {
        return persistedPath;
    }

    public void setPersistedPath(String persistedPath) {
        this.persistedPath = persistedPath;
    }
}
