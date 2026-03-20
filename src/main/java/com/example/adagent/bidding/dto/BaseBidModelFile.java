package com.example.adagent.bidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseBidModelFile {

    private String version = "1.0";
    private String updatedAt;
    private List<BaseBidEntry> entries = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<BaseBidEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<BaseBidEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BaseBidEntry extends BidDimensionRow {
        private double baseBid;

        public double getBaseBid() {
            return baseBid;
        }

        public void setBaseBid(double baseBid) {
            this.baseBid = baseBid;
        }
    }
}
