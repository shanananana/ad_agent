package com.shanananana.adagent.creative.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 「智能生成描述」<strong>请求体</strong>：携带 {@code userId}、可选计划/广告组/内容库 ID、版位代码、用户草稿等，
 * 由 {@link com.shanananana.adagent.creative.CreativePromptSuggestService} 拉取高表现素材并拼装 PromptTemplate。
 */
public class SuggestPromptRequest {

    @JsonProperty("userId")
    private String userId;

    /** 限定统计范围：计划 ID，可选 */
    @JsonProperty("campaignId")
    private String campaignId;

    /** 限定统计范围：广告组 ID，可选（通常与当前要出图的组一致） */
    @JsonProperty("adGroupId")
    private String adGroupId;

    /** 引用全局内容目录中的条目，可选 */
    @JsonProperty("contentId")
    private String contentId;

    /** 用户当前草稿/补充说明（画面意图、禁忌等） */
    @JsonProperty("currentDescription")
    private String currentDescription;

    /** 回溯效果数据天数，默认 30 */
    @JsonProperty("days")
    private Integer days;

    /** 参与参考的头部素材条数，默认 8 */
    @JsonProperty("topK")
    private Integer topK;

    /**
     * 资源位 / 版位（如 FEED、SPLASH、BANNER），用于在提示中注入建议长宽比与安全区说明。
     */
    @JsonProperty("placement")
    private String placement;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getAdGroupId() {
        return adGroupId;
    }

    public void setAdGroupId(String adGroupId) {
        this.adGroupId = adGroupId;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getCurrentDescription() {
        return currentDescription;
    }

    public void setCurrentDescription(String currentDescription) {
        this.currentDescription = currentDescription;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public String getPlacement() {
        return placement;
    }

    public void setPlacement(String placement) {
        this.placement = placement;
    }
}
