package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 投放<strong>基础数据</strong> JSON 根结构（{@code campaigns.json}）：包含多个 {@link Campaign}，
 * 树形描述计划 → 广告组 → 广告；素材通过广告组上的 {@code creativeIds} 引用全局素材 UUID（有序，靠前优先）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CampaignBase {

    private String version = "1.0";
    private String updatedAt;
    private List<Campaign> campaigns = new ArrayList<>();

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

    public List<Campaign> getCampaigns() {
        return campaigns;
    }

    public void setCampaigns(List<Campaign> campaigns) {
        this.campaigns = campaigns != null ? campaigns : new ArrayList<>();
    }

    /** 单个投放计划：预算、状态、排期及其下 {@link AdGroup} 列表。 */
    public static class Campaign {
        private String id;
        private String name;
        private String status = "ACTIVE";
        private Double dailyBudget;
        private String startDate;
        private String endDate;
        private List<AdGroup> adGroups = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Double getDailyBudget() {
            return dailyBudget;
        }

        public void setDailyBudget(Double dailyBudget) {
            this.dailyBudget = dailyBudget;
        }

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public List<AdGroup> getAdGroups() {
            return adGroups;
        }

        public void setAdGroups(List<AdGroup> adGroups) {
            this.adGroups = adGroups != null ? adGroups : new ArrayList<>();
        }
    }

    /** 广告组：定向、绑定的全局素材 ID 列表、其下 {@link Ad} 列表。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdGroup {
        private String id;
        private String name;
        private String status = "ACTIVE";
        private Targeting targeting;
        /** 全局素材 UUID 列表，顺序表示优先级 */
        private List<String> creativeIds = new ArrayList<>();
        private List<Ad> ads = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Targeting getTargeting() {
            return targeting;
        }

        public void setTargeting(Targeting targeting) {
            this.targeting = targeting;
        }

        public List<String> getCreativeIds() {
            if (creativeIds == null) {
                creativeIds = new ArrayList<>();
            }
            return creativeIds;
        }

        public void setCreativeIds(List<String> creativeIds) {
            this.creativeIds = creativeIds != null ? creativeIds : new ArrayList<>();
        }

        public List<Ad> getAds() {
            return ads;
        }

        public void setAds(List<Ad> ads) {
            this.ads = ads != null ? ads : new ArrayList<>();
        }
    }

    /** 广告单元：隶属于某 {@link AdGroup} 的最小投放实体（本项目中结构从简）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ad {
        private String id;
        private String name;
        private String status = "ACTIVE";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /** 定向条件：年龄、性别、地域、渠道等列表，供展示与效果维度对齐。 */
    public static class Targeting {
        private List<String> ageRanges = new ArrayList<>();
        private List<String> genders = new ArrayList<>();
        private List<String> regions = new ArrayList<>();
        private List<String> channels = new ArrayList<>();

        public List<String> getAgeRanges() {
            return ageRanges;
        }

        public void setAgeRanges(List<String> ageRanges) {
            this.ageRanges = ageRanges != null ? ageRanges : new ArrayList<>();
        }

        public List<String> getGenders() {
            return genders;
        }

        public void setGenders(List<String> genders) {
            this.genders = genders != null ? genders : new ArrayList<>();
        }

        public List<String> getRegions() {
            return regions;
        }

        public void setRegions(List<String> regions) {
            this.regions = regions != null ? regions : new ArrayList<>();
        }

        public List<String> getChannels() {
            return channels;
        }

        public void setChannels(List<String> channels) {
            this.channels = channels != null ? channels : new ArrayList<>();
        }
    }
}
