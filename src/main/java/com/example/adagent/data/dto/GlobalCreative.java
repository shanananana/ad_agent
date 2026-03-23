package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * <strong>全局素材目录</strong>中的单条创意：{@code id} 为 UUID，与 {@link CampaignBase.AdGroup#getCreativeIds()}、
 * 效果数据 {@link PerformanceData.PerformanceRow#getCreativeId()} 引用同一标识；可含标题、描述、持久化图片 URL 等。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlobalCreative {

    private String id;
    private String type = "IMAGE";
    private String title;
    private String description;
    private String status = "APPROVED";
    /** 相对数据根目录的路径，如 creative/assets/{user}/xxx.png */
    private String imagePath;
    /** 生成时的 prompt 快照 */
    private String promptSnapshot;
    /** 可选：关联的内容 ID（UUID） */
    private String contentId;
    /** 可选：资源位/版位标识 */
    private String placement;
    /** 异步任务状态预留：submitted | queued | generating | done | failed */
    private String taskStatus;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getPromptSnapshot() {
        return promptSnapshot;
    }

    public void setPromptSnapshot(String promptSnapshot) {
        this.promptSnapshot = promptSnapshot;
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

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }
}
