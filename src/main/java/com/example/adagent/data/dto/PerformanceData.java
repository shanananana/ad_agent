package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 效果数据存储结构：按计划/广告组/广告/素材 + 渠道 + 年龄 + 日期。
 * 支持按用户隔离：不同 userId 对应不同文件，见 DataPathConfig.getPerformanceDataPath(userId)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PerformanceData {

    /** 数据格式版本，如 "1.0" */
    private String version = "1.0";
    /** 本文件最后生成/更新时间（ISO-8601） */
    private String generatedAt;
    /** 效果明细行列表，每条为某一计划下某渠道某年龄段某天的聚合指标 */
    private List<PerformanceRow> data = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<PerformanceRow> getData() {
        return data;
    }

    public void setData(List<PerformanceRow> data) {
        this.data = data != null ? data : new ArrayList<>();
    }

    /**
     * 单条效果明细：计划/广告组/广告/素材 + 渠道 + 年龄段 + 日期的展示、点击、消耗、转化等。
     */
    public static class PerformanceRow {
        /** 计划 ID，如 c1、c2 */
        private String campaignId;
        /** 广告组 ID，如 ag1 */
        private String adGroupId;
        /** 广告 ID，如 a1 */
        private String adId;
        /** 创意/素材 ID，如 cr1 */
        private String creativeId;
        /** 投放渠道/端：APP、H5、PC */
        private String channel;
        /** 受众年龄段，如 18-24、25-34、35-44 */
        private String ageRange;
        /** 统计日期，yyyy-MM-dd */
        private String date;
        /** 展示量（曝光次数） */
        private long impressions;
        /** 点击量 */
        private long clicks;
        /** 点击率，clicks/impressions */
        private double ctr;
        /** 消耗（元） */
        private double cost;
        /** 转化数（按业务定义的转化行为次数） */
        private long conversions;
        /** 投资回报率，通常为 转化价值/消耗 */
        private double roi;

        public String getCampaignId() { return campaignId; }
        public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
        public String getAdGroupId() { return adGroupId; }
        public void setAdGroupId(String adGroupId) { this.adGroupId = adGroupId; }
        public String getAdId() { return adId; }
        public void setAdId(String adId) { this.adId = adId; }
        public String getCreativeId() { return creativeId; }
        public void setCreativeId(String creativeId) { this.creativeId = creativeId; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
        public String getAgeRange() { return ageRange; }
        public void setAgeRange(String ageRange) { this.ageRange = ageRange; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public long getImpressions() { return impressions; }
        public void setImpressions(long impressions) { this.impressions = impressions; }
        public long getClicks() { return clicks; }
        public void setClicks(long clicks) { this.clicks = clicks; }
        public double getCtr() { return ctr; }
        public void setCtr(double ctr) { this.ctr = ctr; }
        public double getCost() { return cost; }
        public void setCost(double cost) { this.cost = cost; }
        public long getConversions() { return conversions; }
        public void setConversions(long conversions) { this.conversions = conversions; }
        public double getRoi() { return roi; }
        public void setRoi(double roi) { this.roi = roi; }
    }
}
