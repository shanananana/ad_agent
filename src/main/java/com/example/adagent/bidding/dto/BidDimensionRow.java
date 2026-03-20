package com.example.adagent.bidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * 出价策略维度：人群 × 时段(0-23) × 设备。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BidDimensionRow {

    private String audience;
    /** 小时 0-23 */
    private int hourSlot;
    private String device;

    public BidDimensionRow() {
    }

    public BidDimensionRow(String audience, int hourSlot, String device) {
        this.audience = audience;
        this.hourSlot = hourSlot;
        this.device = device;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public int getHourSlot() {
        return hourSlot;
    }

    public void setHourSlot(int hourSlot) {
        this.hourSlot = hourSlot;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String dimensionKey() {
        return audience + "|" + hourSlot + "|" + device;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BidDimensionRow that = (BidDimensionRow) o;
        return hourSlot == that.hourSlot
                && Objects.equals(audience, that.audience)
                && Objects.equals(device, that.device);
    }

    @Override
    public int hashCode() {
        return Objects.hash(audience, hourSlot, device);
    }
}
