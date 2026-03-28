package com.shanananana.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户维度<strong>全局素材目录</strong> JSON 顶层（{@code creatives.json}）：版本元数据与 {@link GlobalCreative} 列表，
 * 与计划树中广告组的 {@code creativeIds} 引用同一套 UUID。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlobalCreativeFile {

    private String version = "1.0";
    private String updatedAt;
    private List<GlobalCreative> creatives = new ArrayList<>();

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

    public List<GlobalCreative> getCreatives() {
        return creatives;
    }

    public void setCreatives(List<GlobalCreative> creatives) {
        this.creatives = creatives != null ? creatives : new ArrayList<>();
    }
}
