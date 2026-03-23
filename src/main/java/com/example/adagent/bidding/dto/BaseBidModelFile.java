package com.example.adagent.bidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动调价 <strong>基础出价 B</strong> 的 JSON 根结构，对应计划目录或全局下的 {@code base_bid_model.json}；
 * {@link BaseBidEntry} 为人群×时段×设备 网格中一格的 {@code baseBid}。
 */
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

    /** 单维度格上的基础出价 {@code B}，维度键继承自 {@link BidDimensionRow}。 */
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
