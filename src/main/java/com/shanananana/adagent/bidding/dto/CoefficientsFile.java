package com.shanananana.adagent.bidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动调价 <strong>出价系数 α</strong> 的 JSON 根结构，对应 {@code coefficients.json}；
 * 与 {@link BaseBidModelFile} 同维度网格，{@link CoefficientEntry} 存放每格的乘子 α。
 */
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

    /** 单维度格上的出价乘子 {@code α}，与 {@link BaseBidModelFile.BaseBidEntry} 同键。 */
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
