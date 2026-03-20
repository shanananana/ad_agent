package com.example.adagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 自动调价助手（B×α）任务配置，前缀 ad-agent.bidding。
 */
@Configuration
@ConfigurationProperties(prefix = "ad-agent.bidding")
public class BiddingProperties {

    private boolean enabled = true;
    /** Spring @Scheduled cron（默认每小时整点） */
    private String cron = "0 0 * * * ?";
    private double alphaMin = 0.5;
    private double alphaMax = 1.5;
    /** 单周期相对上一版 α 最大变化比例，如 0.2 表示 ±20% */
    private double maxRelativeChange = 0.2;
    private int jobLogMaxEntries = 30;

    /**
     * 定时任务遍历「谁名下的计划」：与对话页 userId 一致；空则读全局 data/base/campaigns.json。
     */
    private String scheduleUserId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public double getAlphaMin() {
        return alphaMin;
    }

    public void setAlphaMin(double alphaMin) {
        this.alphaMin = alphaMin;
    }

    public double getAlphaMax() {
        return alphaMax;
    }

    public void setAlphaMax(double alphaMax) {
        this.alphaMax = alphaMax;
    }

    public double getMaxRelativeChange() {
        return maxRelativeChange;
    }

    public void setMaxRelativeChange(double maxRelativeChange) {
        this.maxRelativeChange = maxRelativeChange;
    }

    public int getJobLogMaxEntries() {
        return jobLogMaxEntries;
    }

    public void setJobLogMaxEntries(int jobLogMaxEntries) {
        this.jobLogMaxEntries = jobLogMaxEntries;
    }

    public String getScheduleUserId() {
        return scheduleUserId;
    }

    public void setScheduleUserId(String scheduleUserId) {
        this.scheduleUserId = scheduleUserId != null ? scheduleUserId : "";
    }
}
