package com.example.adagent.bidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EffectSnapshotFile {

    private String version = "1.0";
    /** 快照覆盖窗口结束时间 ISO-8601 */
    private String windowEnd;
    private String generatedAt;
    private List<EffectSnapshotEntry> entries = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<EffectSnapshotEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<EffectSnapshotEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EffectSnapshotEntry extends BidDimensionRow {
        private long impressions;
        private long clicks;
        private double cost;
        private double ctr;
        private double roi;

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
}
