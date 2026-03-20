package com.example.adagent.controller;

import com.example.adagent.bidding.BidCoefficientJobService;
import com.example.adagent.bidding.BidStrategyRepository;
import com.example.adagent.bidding.dto.BaseBidModelFile;
import com.example.adagent.bidding.dto.CoefficientJobLogFile;
import com.example.adagent.bidding.dto.CoefficientsFile;
import com.example.adagent.bidding.dto.EffectSnapshotFile;
import com.example.adagent.data.AdDataRepository;
import com.example.adagent.data.PerformanceDataRepository;
import com.example.adagent.data.dto.CampaignBase;
import com.example.adagent.data.dto.PerformanceData;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ad-agent/bid-strategy")
public class BidStrategyController {

    private final BidStrategyRepository bidStrategyRepository;
    private final BidCoefficientJobService bidCoefficientJobService;
    private final AdDataRepository adDataRepository;
    private final PerformanceDataRepository performanceDataRepository;

    public BidStrategyController(
            BidStrategyRepository bidStrategyRepository,
            BidCoefficientJobService bidCoefficientJobService,
            AdDataRepository adDataRepository,
            PerformanceDataRepository performanceDataRepository) {
        this.bidStrategyRepository = bidStrategyRepository;
        this.bidCoefficientJobService = bidCoefficientJobService;
        this.adDataRepository = adDataRepository;
        this.performanceDataRepository = performanceDataRepository;
    }

    /** 兼容：全局 B×α（data/bid 根目录） */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return buildBidOverview(null);
    }

    @GetMapping("/job-log")
    public Map<String, Object> jobLog(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        return sliceJobLog(bidStrategyRepository.loadJobLog(null), limit);
    }

    @PostMapping("/run-job")
    public Map<String, Object> runJob(@RequestBody(required = false) Map<String, Object> body) {
        boolean regen = body != null && Boolean.TRUE.equals(body.get("regenerateSnapshot"));
        return runJobInternal(() -> bidCoefficientJobService.runCoefficientUpdateJob(regen));
    }

    @PostMapping("/regenerate-snapshot")
    public Map<String, String> regenerateSnapshot() {
        bidCoefficientJobService.regenerateSnapshotOnly();
        Map<String, String> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("message", "effect_snapshot 已重新生成");
        return ok;
    }

    @GetMapping("/campaigns")
    public Map<String, Object> listCampaigns(@RequestParam(value = "userId", required = false) String userId) {
        CampaignBase base = adDataRepository.loadBase(normalizeUserId(userId));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (base.getCampaigns() != null) {
            for (CampaignBase.Campaign c : base.getCampaigns()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", c.getId());
                m.put("name", c.getName());
                m.put("status", c.getStatus());
                m.put("dailyBudget", c.getDailyBudget());
                int agCount = c.getAdGroups() == null ? 0 : c.getAdGroups().size();
                m.put("adGroupCount", agCount);
                rows.add(m);
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put("campaigns", rows);
        return out;
    }

    @GetMapping("/campaigns/{campaignId}/detail")
    public Map<String, Object> campaignDetail(
            @PathVariable String campaignId,
            @RequestParam(value = "userId", required = false) String userId) {
        CampaignBase.Campaign campaign = requireCampaign(normalizeUserId(userId), campaignId);
        Map<String, Object> perf =
                performanceDataRepository.campaignPerformanceOverview(normalizeUserId(userId), campaignId, 120);
        @SuppressWarnings("unchecked")
        List<PerformanceData.PerformanceRow> recent = (List<PerformanceData.PerformanceRow>) perf.remove("recentRows");

        Map<String, Object> summary = new HashMap<>();
        summary.put("rowCount", perf.get("rowCount"));
        summary.put("totalImpressions", perf.get("totalImpressions"));
        summary.put("totalClicks", perf.get("totalClicks"));
        summary.put("totalCost", perf.get("totalCost"));
        summary.put("totalConversions", perf.get("totalConversions"));
        summary.put("ctr", perf.get("ctr"));
        summary.put("roi", perf.get("roi"));

        Map<String, Object> out = new HashMap<>();
        out.put("campaign", campaign);
        out.put("performanceSummary", summary);
        out.put("performanceRecent", recent != null ? recent : List.of());
        return out;
    }

    @GetMapping("/campaigns/{campaignId}/overview")
    public Map<String, Object> campaignBidOverview(
            @PathVariable String campaignId,
            @RequestParam(value = "userId", required = false) String userId) {
        requireCampaign(normalizeUserId(userId), campaignId);
        return buildBidOverview(campaignId);
    }

    @GetMapping("/campaigns/{campaignId}/job-log")
    public Map<String, Object> campaignJobLog(
            @PathVariable String campaignId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        requireCampaign(normalizeUserId(userId), campaignId);
        return sliceJobLog(bidStrategyRepository.loadJobLog(campaignId), limit);
    }

    @PostMapping("/campaigns/{campaignId}/run-job")
    public Map<String, Object> campaignRunJob(
            @PathVariable String campaignId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestBody(required = false) Map<String, Object> body) {
        CampaignBase.Campaign c = requireCampaign(normalizeUserId(userId), campaignId);
        boolean regen = body != null && Boolean.TRUE.equals(body.get("regenerateSnapshot"));
        return runJobInternal(
                () -> bidCoefficientJobService.runCoefficientUpdateJob(regen, campaignId, c.getName()));
    }

    @PostMapping("/campaigns/{campaignId}/regenerate-snapshot")
    public Map<String, String> campaignRegenerateSnapshot(
            @PathVariable String campaignId,
            @RequestParam(value = "userId", required = false) String userId) {
        requireCampaign(normalizeUserId(userId), campaignId);
        bidCoefficientJobService.regenerateSnapshotOnly(campaignId);
        Map<String, String> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("message", "已重新生成该计划的 effect_snapshot");
        return ok;
    }

    /** 按当前基础数据为计划追加合成效果明细（最近 7 天），便于详情页有数可看 */
    @PostMapping("/campaigns/{campaignId}/sync-performance")
    public Map<String, String> campaignSyncPerformance(
            @PathVariable String campaignId,
            @RequestParam(value = "userId", required = false) String userId) {
        requireCampaign(normalizeUserId(userId), campaignId);
        performanceDataRepository.generateAndAppendForCampaign(campaignId, normalizeUserId(userId));
        Map<String, String> ok = new HashMap<>();
        ok.put("status", "success");
        ok.put("message", "已为计划追加效果样本，请刷新详情");
        return ok;
    }

    private static String normalizeUserId(String userId) {
        return userId != null && !userId.isBlank() ? userId : null;
    }

    private CampaignBase.Campaign requireCampaign(String userId, String campaignId) {
        return adDataRepository
                .findCampaign(campaignId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计划不存在: " + campaignId));
    }

    private Map<String, Object> buildBidOverview(String campaignId) {
        BaseBidModelFile base = bidStrategyRepository.loadBaseModel(campaignId);
        CoefficientsFile coeffs = bidStrategyRepository.loadCoefficients(campaignId);
        EffectSnapshotFile snap = bidStrategyRepository.loadEffectSnapshot(campaignId);

        Map<String, EffectSnapshotFile.EffectSnapshotEntry> snapMap = new HashMap<>();
        if (snap.getEntries() != null) {
            for (EffectSnapshotFile.EffectSnapshotEntry e : snap.getEntries()) {
                snapMap.put(key(e.getAudience(), e.getHourSlot(), e.getDevice()), e);
            }
        }
        Map<String, BaseBidModelFile.BaseBidEntry> baseMap = new HashMap<>();
        if (base.getEntries() != null) {
            for (BaseBidModelFile.BaseBidEntry e : base.getEntries()) {
                baseMap.put(key(e.getAudience(), e.getHourSlot(), e.getDevice()), e);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        if (coeffs.getEntries() != null) {
            for (CoefficientsFile.CoefficientEntry ce : coeffs.getEntries()) {
                String k = key(ce.getAudience(), ce.getHourSlot(), ce.getDevice());
                BaseBidModelFile.BaseBidEntry be = baseMap.get(k);
                EffectSnapshotFile.EffectSnapshotEntry se = snapMap.get(k);
                double b = be != null ? be.getBaseBid() : 1.0;
                double a = ce.getAlpha();
                Map<String, Object> row = new HashMap<>();
                row.put("audience", ce.getAudience());
                row.put("hourSlot", ce.getHourSlot());
                row.put("device", ce.getDevice());
                row.put("baseBid", b);
                row.put("alpha", a);
                row.put("effectiveBid", Math.round(b * a * 10000) / 10000.0);
                if (se != null) {
                    row.put("impressions", se.getImpressions());
                    row.put("clicks", se.getClicks());
                    row.put("cost", se.getCost());
                    row.put("ctr", se.getCtr());
                    row.put("roi", se.getRoi());
                } else {
                    row.put("impressions", 0);
                    row.put("clicks", 0);
                    row.put("cost", 0.0);
                    row.put("ctr", 0.0);
                    row.put("roi", 0.0);
                }
                rows.add(row);
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("campaignId", campaignId);
        out.put("coefficientsVersion", coeffs.getVersion());
        out.put("coefficientsUpdatedAt", coeffs.getUpdatedAt());
        out.put("snapshotWindowEnd", snap.getWindowEnd());
        out.put("snapshotGeneratedAt", snap.getGeneratedAt());
        out.put("rows", rows);
        return out;
    }

    private static String key(String aud, int h, String dev) {
        return aud + "|" + h + "|" + dev;
    }

    private Map<String, Object> sliceJobLog(CoefficientJobLogFile file, int limit) {
        List<CoefficientJobLogFile.CoefficientJobEntry> jobs = file.getJobs();
        if (jobs == null) {
            jobs = List.of();
        }
        int n = Math.max(1, Math.min(limit, 100));
        List<CoefficientJobLogFile.CoefficientJobEntry> slice =
                jobs.stream().limit(n).collect(Collectors.toList());
        Map<String, Object> out = new HashMap<>();
        out.put("jobs", slice);
        return out;
    }

    private Map<String, Object> runJobInternal(java.util.function.Supplier<CoefficientJobLogFile.CoefficientJobEntry> run) {
        try {
            CoefficientJobLogFile.CoefficientJobEntry entry = run.get();
            Map<String, Object> out = new HashMap<>();
            out.put("status", entry.isSuccess() ? "ok" : "error");
            out.put("job", entry);
            return out;
        } catch (RuntimeException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error");
            err.put("message", e.getMessage());
            return err;
        }
    }
}
