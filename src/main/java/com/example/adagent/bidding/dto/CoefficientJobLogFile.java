package com.example.adagent.bidding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoefficientJobLogFile {

    private List<CoefficientJobEntry> jobs = new ArrayList<>();

    public List<CoefficientJobEntry> getJobs() {
        return jobs;
    }

    public void setJobs(List<CoefficientJobEntry> jobs) {
        this.jobs = jobs != null ? jobs : new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CoefficientJobEntry {
        private String jobId;
        private String startedAt;
        /** 非空表示该条为某计划的调价任务；空表示历史全局任务 */
        private String campaignId;
        private boolean success;
        /** LLM rationale：须含上调/下调及原因（见自动调价 prompt） */
        private String llmRationale;
        /** 输入给 LLM 的效果摘要（短文本） */
        private String inputSummary;
        /** 生效前系数文件版本 */
        private long coefficientsVersionBefore;
        /** 生效后系数文件版本 */
        private long coefficientsVersionAfter;
        /** 程序根据旧新系数对比生成的「上调 / 下调」概要（与 LLM 说明互补） */
        private String alphaChangeSummary;
        private String errorMessage;

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getCampaignId() {
            return campaignId;
        }

        public void setCampaignId(String campaignId) {
            this.campaignId = campaignId;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getLlmRationale() {
            return llmRationale;
        }

        public void setLlmRationale(String llmRationale) {
            this.llmRationale = llmRationale;
        }

        public String getInputSummary() {
            return inputSummary;
        }

        public void setInputSummary(String inputSummary) {
            this.inputSummary = inputSummary;
        }

        public long getCoefficientsVersionBefore() {
            return coefficientsVersionBefore;
        }

        public void setCoefficientsVersionBefore(long coefficientsVersionBefore) {
            this.coefficientsVersionBefore = coefficientsVersionBefore;
        }

        public long getCoefficientsVersionAfter() {
            return coefficientsVersionAfter;
        }

        public void setCoefficientsVersionAfter(long coefficientsVersionAfter) {
            this.coefficientsVersionAfter = coefficientsVersionAfter;
        }

        public String getAlphaChangeSummary() {
            return alphaChangeSummary;
        }

        public void setAlphaChangeSummary(String alphaChangeSummary) {
            this.alphaChangeSummary = alphaChangeSummary;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
