package com.shanananana.adagent.bidding;

import com.shanananana.adagent.bidding.dto.CoefficientJobLogFile;
import com.shanananana.adagent.data.PerformanceDataRepository;
import com.shanananana.adagent.data.dto.PerformanceData;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * <strong>计划效果时序</strong>：从 {@link com.shanananana.adagent.data.PerformanceDataRepository} 读取明细，按日聚合展示/点击/消耗/ROI，
 * 并叠加调价成功日等事件标记，供 {@code bid-strategy.html} 折线图与竖线使用。
 */
@Service
public class CampaignPerformanceSeriesService {

    private static final int DAYS_MIN = 7;
    private static final int DAYS_MAX = 90;

    private final PerformanceDataRepository performanceDataRepository;
    private final BidStrategyRepository bidStrategyRepository;

    public CampaignPerformanceSeriesService(
            PerformanceDataRepository performanceDataRepository,
            BidStrategyRepository bidStrategyRepository) {
        this.performanceDataRepository = performanceDataRepository;
        this.bidStrategyRepository = bidStrategyRepository;
    }

    /**
     * @param aggregateCampaign true 时整计划按日汇总；false 时按 adGroupId、adId、channel、ageRange 及可选 creativeId 与 PerformanceRow 对齐过滤
     */
    public Map<String, Object> buildSeries(
            String userId,
            String campaignId,
            String adGroupId,
            String adId,
            String creativeId,
            String channel,
            String ageRange,
            int days,
            String metric,
            boolean aggregateCampaign) {
        int d = Math.max(DAYS_MIN, Math.min(DAYS_MAX, days));
        String m = normalizeMetric(metric);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1L);

        List<PerformanceData.PerformanceRow> rows =
                performanceDataRepository.rowsForCampaign(userId, campaignId);

        List<PerformanceData.PerformanceRow> filtered = new ArrayList<>();
        for (PerformanceData.PerformanceRow r : rows) {
            if (!rowInDateRange(r, start, end)) {
                continue;
            }
            if (aggregateCampaign) {
                filtered.add(r);
                continue;
            }
            if (!dimMatch(adGroupId, r.getAdGroupId())) {
                continue;
            }
            if (!dimMatch(adId, r.getAdId())) {
                continue;
            }
            if (!dimMatch(channel, r.getChannel())) {
                continue;
            }
            if (!dimMatch(ageRange, r.getAgeRange())) {
                continue;
            }
            if (StringUtils.hasText(creativeId) && !creativeId.equals(r.getCreativeId())) {
                continue;
            }
            filtered.add(r);
        }

        Map<String, DailyBucket> byDate = new TreeMap<>();
        for (PerformanceData.PerformanceRow r : filtered) {
            String dateKey = r.getDate();
            if (!StringUtils.hasText(dateKey)) {
                continue;
            }
            DailyBucket b = byDate.computeIfAbsent(dateKey, k -> new DailyBucket());
            b.impressions += r.getImpressions();
            b.clicks += r.getClicks();
            b.cost += r.getCost();
            b.conversions += r.getConversions();
        }

        List<String> dates = new ArrayList<>(byDate.keySet());
        List<Double> values = new ArrayList<>();
        for (String date : dates) {
            DailyBucket b = byDate.get(date);
            values.add(computeMetric(b, m));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("campaignId", campaignId);
        out.put("scope", aggregateCampaign ? "campaign" : "row");
        out.put("metric", m);
        out.put("days", d);
        out.put("dates", dates);
        out.put("values", values);
        out.put("events", buildEvents(campaignId));
        return out;
    }

    /**
     * 按「广告组|广告|创意|渠道|年龄段」分组，在最近 {@code days} 个自然日内按日历序每日聚合后得到指标序列，供表格内迷你折线。
     * Key 与前端 {@code perfRowDimKey} 一致，字段空值用空串。
     */
    public Map<String, Object> buildRowSparklines(
            String userId, String campaignId, int days, String metric) {
        int d = Math.max(3, Math.min(30, days));
        String m = normalizeMetric(metric);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1L);

        List<PerformanceData.PerformanceRow> rows =
                performanceDataRepository.rowsForCampaign(userId, campaignId);

        Map<String, TreeMap<String, DailyBucket>> grouped = new HashMap<>();
        for (PerformanceData.PerformanceRow r : rows) {
            if (!rowInDateRange(r, start, end)) {
                continue;
            }
            String dateKey = r.getDate();
            if (!StringUtils.hasText(dateKey)) {
                continue;
            }
            String key = rowDimKey(r);
            TreeMap<String, DailyBucket> byDate = grouped.computeIfAbsent(key, k -> new TreeMap<>());
            DailyBucket b = byDate.computeIfAbsent(dateKey, k -> new DailyBucket());
            b.impressions += r.getImpressions();
            b.clicks += r.getClicks();
            b.cost += r.getCost();
            b.conversions += r.getConversions();
        }

        Map<String, List<Double>> series = new HashMap<>();
        for (Map.Entry<String, TreeMap<String, DailyBucket>> e : grouped.entrySet()) {
            List<Double> vals = new ArrayList<>();
            for (DailyBucket b : e.getValue().values()) {
                vals.add(computeMetric(b, m));
            }
            series.put(e.getKey(), vals);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("metric", m);
        out.put("days", d);
        out.put("series", series);
        return out;
    }

    private static String rowDimKey(PerformanceData.PerformanceRow r) {
        return nullToEmpty(r.getAdGroupId())
                + "|"
                + nullToEmpty(r.getAdId())
                + "|"
                + nullToEmpty(r.getCreativeId())
                + "|"
                + nullToEmpty(r.getChannel())
                + "|"
                + nullToEmpty(r.getAgeRange());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private List<Map<String, Object>> buildEvents(String campaignId) {
        List<Map<String, Object>> list = new ArrayList<>();
        CoefficientJobLogFile file = bidStrategyRepository.loadJobLog(campaignId);
        if (file.getJobs() == null) {
            return list;
        }
        for (CoefficientJobLogFile.CoefficientJobEntry e : file.getJobs()) {
            if (!e.isSuccess()) {
                continue;
            }
            String started = e.getStartedAt();
            if (!StringUtils.hasText(started)) {
                continue;
            }
            String datePart = started.length() >= 10 ? started.substring(0, 10) : started;
            Map<String, Object> ev = new HashMap<>();
            ev.put("at", started);
            ev.put("date", datePart);
            ev.put(
                    "label",
                    "调价 v"
                            + e.getCoefficientsVersionBefore()
                            + "→v"
                            + e.getCoefficientsVersionAfter());
            list.add(ev);
            break;
        }
        return list;
    }

    private static boolean rowInDateRange(PerformanceData.PerformanceRow r, LocalDate start, LocalDate end) {
        if (!StringUtils.hasText(r.getDate())) {
            return false;
        }
        try {
            LocalDate ld = LocalDate.parse(r.getDate());
            return !ld.isBefore(start) && !ld.isAfter(end);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static boolean dimMatch(String filter, String value) {
        if (!StringUtils.hasText(filter)) {
            return true;
        }
        return filter.equals(value);
    }

    private static String normalizeMetric(String metric) {
        if (!StringUtils.hasText(metric)) {
            return "roi";
        }
        return switch (metric.trim().toLowerCase()) {
            case "ctr" -> "ctr";
            case "cost" -> "cost";
            case "impressions" -> "impressions";
            case "clicks" -> "clicks";
            case "conversions" -> "conversions";
            default -> "roi";
        };
    }

    private static double computeMetric(DailyBucket b, String m) {
        double ctr = b.impressions > 0 ? (double) b.clicks / b.impressions : 0.0;
        double roi = b.cost > 0 ? b.conversions * 10.0 / b.cost : 0.0;
        return switch (m) {
            case "ctr" -> Math.round(ctr * 10000) / 10000.0;
            case "cost" -> Math.round(b.cost * 100) / 100.0;
            case "impressions" -> (double) b.impressions;
            case "clicks" -> (double) b.clicks;
            case "conversions" -> (double) b.conversions;
            default -> Math.round(roi * 10000) / 10000.0;
        };
    }

    private static final class DailyBucket {
        long impressions;
        long clicks;
        double cost;
        long conversions;
    }
}
