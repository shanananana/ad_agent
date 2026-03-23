package com.example.adagent.data.dto;

/**
 * 按素材聚合后的效果得分，用于 prompt 推荐等场景。
 */
public class CreativePerformanceScore {

    private String creativeId;
    private long impressions;
    private long clicks;
    private double cost;
    private long conversions;
    private double ctr;
    private double roi;

    public String getCreativeId() {
        return creativeId;
    }

    public void setCreativeId(String creativeId) {
        this.creativeId = creativeId;
    }

    public long getImpressions() {
        return impressions;
    }

    public void setImpressions(long impressions) {
        this.impressions = impressions;
    }

    public long getClicks() {
        return clicks;
    }

    public void setClicks(long clicks) {
        this.clicks = clicks;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public long getConversions() {
        return conversions;
    }

    public void setConversions(long conversions) {
        this.conversions = conversions;
    }

    public double getCtr() {
        return ctr;
    }

    public void setCtr(double ctr) {
        this.ctr = ctr;
    }

    public double getRoi() {
        return roi;
    }

    public void setRoi(double roi) {
        this.roi = roi;
    }
}
