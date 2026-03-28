package com.shanananana.adagent.creative.dto;

/**
 * 素材 Tab「技能一键」请求体：仅业务参数，流程由主对话模型 + Markdown 技能决定。
 */
public class MaterialCreativeRequest {

    private String userId;
    private String campaignId;
    private String adGroupId;
    /** 用户已填写的画面描述，可空 */
    private String userPrompt;
    private boolean persist;
    private String title;
    private String contentId;
    private String placement;
    private boolean bindToAdGroupAfterPersist;
    private boolean bindPrepend;

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

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public boolean isPersist() {
        return persist;
    }

    public void setPersist(boolean persist) {
        this.persist = persist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public boolean isBindToAdGroupAfterPersist() {
        return bindToAdGroupAfterPersist;
    }

    public void setBindToAdGroupAfterPersist(boolean bindToAdGroupAfterPersist) {
        this.bindToAdGroupAfterPersist = bindToAdGroupAfterPersist;
    }

    public boolean isBindPrepend() {
        return bindPrepend;
    }

    public void setBindPrepend(boolean bindPrepend) {
        this.bindPrepend = bindPrepend;
    }
}
