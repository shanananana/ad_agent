package com.example.adagent.creative.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 文生图 <strong>HTTP 响应</strong>：生成状态、持久化后的本地访问 URL、新素材 UUID 等；
 * 若返回厂商临时 URL，通常有效期约 24h，客户端宜尽快转存。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenerateImageResponse {

    private String status;
    private String imageUrl;
    private String b64Json;
    private String message;
    /** 持久化成功时返回的全局素材 UUID */
    private String creativeId;
    /** 相对数据根目录的路径，如 creative/assets/{user}/xxx.png */
    private String persistedPath;

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

    public static GenerateImageResponse ok(String imageUrl, String b64Json) {
        GenerateImageResponse r = new GenerateImageResponse();
        r.status = "ok";
        r.imageUrl = imageUrl;
        r.b64Json = b64Json;
        return r;
    }

    public static GenerateImageResponse error(String message) {
        GenerateImageResponse r = new GenerateImageResponse();
        r.status = "error";
        r.message = message;
        return r;
    }

    public String getStatus() {
        return status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getB64Json() {
        return b64Json;
    }

    public String getMessage() {
        return message;
    }
}
