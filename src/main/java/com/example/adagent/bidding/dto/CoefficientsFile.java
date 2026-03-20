package com.example.adagent.bidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoefficientsFile {

    private long version;
    private String updatedAt;
    private List<CoefficientEntry> entries = new ArrayList<>();

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<CoefficientEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<CoefficientEntry> entries) {
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CoefficientEntry extends BidDimensionRow {
        private double alpha = 1.0;

        public double getAlpha() {
            return alpha;
        }

        public void setAlpha(double alpha) {
            this.alpha = alpha;
        }
    }
}
