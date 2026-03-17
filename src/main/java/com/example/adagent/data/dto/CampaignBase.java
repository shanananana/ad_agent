package com.example.adagent.data.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 投放基础数据 DTO：计划、广告组、广告、素材（单文件内嵌结构）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    public static class Campaign {
        private String id;
        private String name;
        private String status = "ACTIVE";
        private Double dailyBudget;
        private String startDate;
        private String endDate;
        private List<AdGroup> adGroups = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Double getDailyBudget() { return dailyBudget; }
        public void setDailyBudget(Double dailyBudget) { this.dailyBudget = dailyBudget; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public List<AdGroup> getAdGroups() { return adGroups; }
        public void setAdGroups(List<AdGroup> adGroups) { this.adGroups = adGroups != null ? adGroups : new ArrayList<>(); }
    }

    public static class AdGroup {
        private String id;
        private String name;
        private String status = "ACTIVE";
        private Targeting targeting;
        private List<Ad> ads = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Targeting getTargeting() { return targeting; }
        public void setTargeting(Targeting targeting) { this.targeting = targeting; }
        public List<Ad> getAds() { return ads; }
        public void setAds(List<Ad> ads) { this.ads = ads != null ? ads : new ArrayList<>(); }
    }

    public static class Ad {
        private String id;
        private String name;
        private String status = "ACTIVE";
        private List<Creative> creatives = new ArrayList<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<Creative> getCreatives() { return creatives; }
        public void setCreatives(List<Creative> creatives) { this.creatives = creatives != null ? creatives : new ArrayList<>(); }
    }

    public static class Creative {
        private String id;
        private String type = "IMAGE";
        private String title;
        private String description;
        private String status = "APPROVED";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class Targeting {
        private List<String> ageRanges = new ArrayList<>();
        private List<String> genders = new ArrayList<>();
        private List<String> regions = new ArrayList<>();
        private List<String> channels = new ArrayList<>();

        public List<String> getAgeRanges() { return ageRanges; }
        public void setAgeRanges(List<String> ageRanges) { this.ageRanges = ageRanges != null ? ageRanges : new ArrayList<>(); }
        public List<String> getGenders() { return genders; }
        public void setGenders(List<String> genders) { this.genders = genders != null ? genders : new ArrayList<>(); }
        public List<String> getRegions() { return regions; }
        public void setRegions(List<String> regions) { this.regions = regions != null ? regions : new ArrayList<>(); }
        public List<String> getChannels() { return channels; }
        public void setChannels(List<String> channels) { this.channels = channels != null ? channels : new ArrayList<>(); }
    }
}
