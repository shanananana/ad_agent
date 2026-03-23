package com.example.adagent.bidding;

import com.example.adagent.bidding.dto.CoefficientJobLogFile;
import com.example.adagent.bidding.dto.CoefficientsFile;
import com.example.adagent.config.BiddingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自动调价<strong>任务编排</strong>：供定时任务与 REST 手动触发，串联快照生成/刷新、
 * {@link BidCoefficientLlmService} 推理、系数文件版本递增与 {@link BidStrategyRepository} 落盘，并追加 {@link com.example.adagent.bidding.dto.CoefficientJobLogFile} 审计条目。
 */
@Service
public class BidCoefficientJobService {

    private static final Logger logger = LoggerFactory.getLogger(BidCoefficientJobService.class);
    private final BidStrategyRepository repository;
    private final EffectSnapshotGenerator snapshotGenerator;
    private final BidCoefficientLlmService llmService;
    private final BiddingProperties biddingProperties;

    public BidCoefficientJobService(
            BidStrategyRepository repository,
            EffectSnapshotGenerator snapshotGenerator,
            BidCoefficientLlmService llmService,
            BiddingProperties biddingProperties) {
        this.repository = repository;
        this.snapshotGenerator = snapshotGenerator;
        this.llmService = llmService;
        this.biddingProperties = biddingProperties;
    }

    /**
     * 全局出价目录（兼容旧 data/bid/*.json）。
     *
     * @return 本次任务日志条目（已追加到文件）
     */
    public CoefficientJobLogFile.CoefficientJobEntry runCoefficientUpdateJob(boolean regenerateSnapshot) {
        return runCoefficientUpdateJob(regenerateSnapshot, null, null);
    }

    /**
     * @param campaignId   非空时读写 data/bid/campaigns/{id}/
     * @param campaignName 传入 LLM 的展示名，可空
     */
    public CoefficientJobLogFile.CoefficientJobEntry runCoefficientUpdateJob(
            boolean regenerateSnapshot, String campaignId, String campaignName) {
        String jobId = UUID.randomUUID().toString();
        String startedAt = Instant.now().toString();
        CoefficientsFile before = repository.loadCoefficients(campaignId);
        long verBefore = before.getVersion();

        CoefficientJobLogFile.CoefficientJobEntry logEntry = new CoefficientJobLogFile.CoefficientJobEntry();
        logEntry.setJobId(jobId);
        logEntry.setStartedAt(startedAt);
        logEntry.setCampaignId(
                campaignId != null && !campaignId.isBlank() ? campaignId : null);
        logEntry.setCoefficientsVersionBefore(verBefore);

        try {
            if (regenerateSnapshot) {
                repository.writeEffectSnapshot(snapshotGenerator.generateFreshSnapshot(campaignId), campaignId);
            }

            var base = repository.loadBaseModel(campaignId);
            var coeffs = repository.loadCoefficients(campaignId);
            var snap = repository.loadEffectSnapshot(campaignId);
            logEntry.setInputSummary(buildInputSummary(snap, campaignId));

            BidCoefficientLlmService.LlmCoefficientResult result =
                    llmService.suggestCoefficients(base, coeffs, snap, campaignId, campaignName);
            if (result == null || result.newCoefficients() == null) {
                logEntry.setSuccess(false);
                logEntry.setErrorMessage("LLM 无有效输出或解析失败");
                logEntry.setCoefficientsVersionAfter(verBefore);
                appendJobLogSafe(logEntry, campaignId);
                logger.warn("【自动调价】任务 {} 失败: {}", jobId, logEntry.getErrorMessage());
                return logEntry;
            }

            CoefficientsFile next = result.newCoefficients();
            next.setVersion(verBefore + 1);
            next.setUpdatedAt(Instant.now().toString());
            logEntry.setAlphaChangeSummary(buildAlphaChangeSummary(coeffs, next));
            logEntry.setLlmRationale(result.rationale());
            repository.writeCoefficients(next, campaignId);
            logEntry.setSuccess(true);
            logEntry.setCoefficientsVersionAfter(next.getVersion());
            appendJobLogSafe(logEntry, campaignId);
            logger.info("【自动调价】任务 {} 成功, version {} -> {}", jobId, verBefore, next.getVersion());
            return logEntry;
        } catch (Exception e) {
            logger.error("【自动调价】任务 {} 异常", jobId, e);
            logEntry.setSuccess(false);
            logEntry.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            logEntry.setCoefficientsVersionAfter(verBefore);
            appendJobLogSafe(logEntry, campaignId);
            throw new RuntimeException("自动调价任务失败: " + jobId, e);
        }
    }

    /** 仅重写 effect_snapshot（合成数据），不调用 LLM */
    public void regenerateSnapshotOnly() {
        regenerateSnapshotOnly(null);
    }

    public void regenerateSnapshotOnly(String campaignId) {
        try {
            repository.writeEffectSnapshot(snapshotGenerator.generateFreshSnapshot(campaignId), campaignId);
            logger.info("【自动调价】已重新生成 effect_snapshot");
        } catch (IOException e) {
            throw new RuntimeException("重写效果快照失败", e);
        }
    }

    private void appendJobLogSafe(CoefficientJobLogFile.CoefficientJobEntry entry, String campaignId) {
        CoefficientJobLogFile file = repository.loadJobLog(campaignId);
        List<CoefficientJobLogFile.CoefficientJobEntry> jobs = file.getJobs();
        if (jobs == null) {
            jobs = new ArrayList<>();
            file.setJobs(jobs);
        }
        jobs.add(0, entry);
        int max = Math.max(1, biddingProperties.getJobLogMaxEntries());
        while (jobs.size() > max) {
            jobs.remove(jobs.size() - 1);
        }
        try {
            repository.writeJobLog(file, campaignId);
        } catch (IOException e) {
            logger.error("【自动调价】写入 job_log 失败", e);
        }
    }

    private static String buildInputSummary(com.example.adagent.bidding.dto.EffectSnapshotFile snap, String campaignId) {
        String prefix =
                (campaignId != null && !campaignId.isBlank()) ? ("计划=" + campaignId + ", ") : "";
        if (snap.getEntries() == null) {
            return prefix + "无快照条目";
        }
        return prefix + "维度数=" + snap.getEntries().size() + ", windowEnd=" + snap.getWindowEnd();
    }

    /**
     * 按维度 key 对比旧新 α，生成「上调 / 下调」概要（与 LLM rationale 互补，便于快速扫一眼）。
     */
    private static String buildAlphaChangeSummary(CoefficientsFile oldF, CoefficientsFile newF) {
        if (oldF.getEntries() == null || newF.getEntries() == null) {
            return "";
        }
        Map<String, Double> oldAlpha = new LinkedHashMap<>();
        for (CoefficientsFile.CoefficientEntry e : oldF.getEntries()) {
            oldAlpha.put(coeffKey(e), e.getAlpha());
        }
        List<String> up = new ArrayList<>();
        List<String> down = new ArrayList<>();
        for (CoefficientsFile.CoefficientEntry e : newF.getEntries()) {
            String k = coeffKey(e);
            Double prev = oldAlpha.get(k);
            if (prev == null) {
                continue;
            }
            double n = e.getAlpha();
            if (Math.abs(prev - n) < 1e-6) {
                continue;
            }
            String dim = e.getAudience() + "/" + e.getHourSlot() + "h/" + e.getDevice() + ": " + prev + "→" + n;
            if (n > prev) {
                up.add(dim);
            } else {
                down.add(dim);
            }
        }
        if (up.isEmpty() && down.isEmpty()) {
            return "各维度 α 未变化（护栏或 LLM 未调整）";
        }
        StringBuilder sb = new StringBuilder();
        if (!up.isEmpty()) {
            sb.append("上调：").append(String.join("；", trimList(up, 10)));
            if (up.size() > 10) {
                sb.append("…");
            }
        }
        if (!down.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("下调：").append(String.join("；", trimList(down, 10)));
            if (down.size() > 10) {
                sb.append("…");
            }
        }
        return sb.toString();
    }

    private static List<String> trimList(List<String> items, int max) {
        if (items.size() <= max) {
            return items;
        }
        return new ArrayList<>(items.subList(0, max));
    }

    private static String coeffKey(CoefficientsFile.CoefficientEntry e) {
        return e.getAudience() + "|" + e.getHourSlot() + "|" + e.getDevice();
    }
}
