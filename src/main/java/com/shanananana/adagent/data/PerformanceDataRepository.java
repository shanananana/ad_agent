package com.shanananana.adagent.data;

import com.shanananana.adagent.config.DataPathConfig;
import com.shanananana.adagent.data.dto.CampaignBase;
import com.shanananana.adagent.data.dto.CreativePerformanceScore;
import com.shanananana.adagent.data.dto.PerformanceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * <strong>效果数据</strong>仓库：按用户或全局路径读写 {@code performance.json}，提供按活动/素材/维度的查询与汇总；
 * 在变更计划时生成模拟明细行，供工具层与看板消费。
 */
@Repository
public class PerformanceDataRepository {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceDataRepository.class);
    private static final String[] CHANNELS = {"APP", "H5", "PC"};
    private static final String[] AGE_RANGES = {"18-24", "25-34", "35-44", "45-54", "55+"};

    private final DataPathConfig dataPathConfig;
    private final AdDataRepository adDataRepository;
    private final ObjectMapper objectMapper;

    public PerformanceDataRepository(DataPathConfig dataPathConfig,
                                     AdDataRepository adDataRepository,
                                     ObjectMapper objectMapper) {
        this.dataPathConfig = dataPathConfig;
        this.adDataRepository = adDataRepository;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** 加载效果数据；userId 为 null/空时读全局文件，否则读该用户独立文件 */
    public PerformanceData loadPerformance(String userId) {
        var path = dataPathConfig.getPerformanceDataPath(userId);
        if (!Files.exists(path)) {
            PerformanceData empty = new PerformanceData();
            empty.setGeneratedAt(Instant.now().toString());
            empty.setData(new ArrayList<>());
            return empty;
        }
        try {
            String json = Files.readString(path);
            PerformanceData data = objectMapper.readValue(json, PerformanceData.class);
            if (data.getData() == null) {
                data.setData(new ArrayList<>());
            }
            return data;
        } catch (Exception e) {
            logger.error("【数据层】读取效果数据失败: {}", path, e);
            PerformanceData empty = new PerformanceData();
            empty.setGeneratedAt(Instant.now().toString());
            empty.setData(new ArrayList<>());
            return empty;
        }
    }

    /** 写入效果数据；userId 为 null/空时写全局文件 */
    public void savePerformance(String userId, PerformanceData data) {
        data.setGeneratedAt(Instant.now().toString());
        var path = dataPathConfig.getPerformanceDataPath(userId);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), data);
            logger.info("【数据层】已写入效果数据: {}", path);
        } catch (IOException e) {
            logger.error("【数据层】写入效果数据失败: {}", path, e);
            throw new RuntimeException("写入效果数据失败", e);
        }
    }

    /**
     * 为指定计划生成最近 7 天效果数据并追加到对应用户（或全局）文件中。
     * @param userId 为 null/空时写入全局 performance.json
     */
    public void generateAndAppendForCampaign(String campaignId, String userId) {
        CampaignBase base = adDataRepository.loadBase(userId);
        CampaignBase.Campaign campaign = base.getCampaigns().stream()
                .filter(c -> campaignId.equals(c.getId()))
                .findFirst()
                .orElse(null);
        if (campaign == null) {
            logger.warn("【数据层】未找到计划: {}", campaignId);
            return;
        }
        List<PerformanceData.PerformanceRow> newRows = new ArrayList<>();
        LocalDate end = LocalDate.now();
        for (int d = 0; d < 7; d++) {
            LocalDate date = end.minusDays(d);
            String dateStr = date.toString();
            walkAndGenerate(campaign, null, null, null, dateStr, newRows);
        }
        PerformanceData perf = loadPerformance(userId);
        perf.getData().addAll(newRows);
        savePerformance(userId, perf);
    }

    /**
     * 为全量计划重新生成效果并覆盖写入；userId 为 null/空时写全局。
     */
    public void generateAllAndOverwrite(String userId) {
        CampaignBase base = adDataRepository.loadBase(userId);
        List<PerformanceData.PerformanceRow> all = new ArrayList<>();
        LocalDate end = LocalDate.now();
        for (CampaignBase.Campaign c : base.getCampaigns()) {
            for (int d = 0; d < 7; d++) {
                String dateStr = end.minusDays(d).toString();
                walkAndGenerate(c, null, null, null, dateStr, all);
            }
        }
        PerformanceData perf = new PerformanceData();
        perf.setData(all);
        savePerformance(userId, perf);
    }

    private void walkAndGenerate(CampaignBase.Campaign campaign,
                                 String adGroupId, String adId, String creativeId,
                                 String dateStr, List<PerformanceData.PerformanceRow> out) {
        List<String> channels = campaign.getAdGroups().stream()
                .filter(ag -> adGroupId == null || adGroupId.equals(ag.getId()))
                .flatMap(ag -> (ag.getTargeting() != null && ag.getTargeting().getChannels() != null)
                        ? ag.getTargeting().getChannels().stream()
                        : java.util.stream.Stream.of("APP", "H5"))
                .distinct()
                .toList();
        if (channels.isEmpty()) {
            channels = List.of("APP", "H5");
        }
        List<String> ages = campaign.getAdGroups().stream()
                .filter(ag -> adGroupId == null || adGroupId.equals(ag.getId()))
                .flatMap(ag -> (ag.getTargeting() != null && ag.getTargeting().getAgeRanges() != null)
                        ? ag.getTargeting().getAgeRanges().stream()
                        : java.util.stream.Stream.of("18-24", "25-34"))
                .distinct()
                .toList();
        if (ages.isEmpty()) {
            ages = List.of("18-24", "25-34");
        }

        for (CampaignBase.AdGroup ag : campaign.getAdGroups()) {
            if (adGroupId != null && !adGroupId.equals(ag.getId())) {
                continue;
            }
            if (adId != null && !adId.isBlank() && !"-".equals(adId)) {
                continue;
            }
            java.util.List<String> cids = ag.getCreativeIds();
            if (cids == null) {
                cids = java.util.List.of();
            }
            for (String crId : cids) {
                if (creativeId != null && !creativeId.equals(crId)) {
                    continue;
                }
                for (String ch : channels) {
                    for (String age : ages) {
                        PerformanceData.PerformanceRow row = new PerformanceData.PerformanceRow();
                        row.setCampaignId(campaign.getId());
                        row.setAdGroupId(ag.getId());
                        row.setAdId("-");
                        row.setCreativeId(crId);
                        row.setChannel(ch);
                        row.setAgeRange(age);
                        row.setDate(dateStr);
                        ThreadLocalRandom r = ThreadLocalRandom.current();
                        row.setImpressions(r.nextLong(5000, 50000));
                        row.setClicks((long) (row.getImpressions() * (0.01 + r.nextDouble(0.03))));
                        row.setCtr(row.getImpressions() > 0 ? (double) row.getClicks() / row.getImpressions() : 0);
                        row.setCost(r.nextDouble(50, 500));
                        row.setConversions(r.nextLong(0, 20));
                        row.setRoi(row.getCost() > 0 ? row.getConversions() * 10.0 / row.getCost() : 0);
                        out.add(row);
                    }
                }
            }
        }
    }

    /** 某计划在效果文件中的明细行（未排序） */
    public List<PerformanceData.PerformanceRow> rowsForCampaign(String userId, String campaignId) {
        if (campaignId == null || campaignId.isBlank()) {
            return List.of();
        }
        return loadPerformance(userId).getData().stream()
                .filter(r -> campaignId.equals(r.getCampaignId()))
                .collect(Collectors.toList());
    }

    /**
     * 汇总某计划效果（与 PerformanceRow 生成逻辑一致：ROI ≈ conversions×10/cost）。
     *
     * @param recentRows 最多返回最近若干条明细供表格展示（按日期倒序）
     */
    public Map<String, Object> campaignPerformanceOverview(String userId, String campaignId, int recentRows) {
        List<PerformanceData.PerformanceRow> all = rowsForCampaign(userId, campaignId);
        long impressions = 0L;
        long clicks = 0L;
        double cost = 0.0;
        long conversions = 0L;
        for (PerformanceData.PerformanceRow r : all) {
            impressions += r.getImpressions();
            clicks += r.getClicks();
            cost += r.getCost();
            conversions += r.getConversions();
        }
        double ctr = impressions > 0 ? (double) clicks / impressions : 0.0;
        double roi = cost > 0 ? conversions * 10.0 / cost : 0.0;

        List<PerformanceData.PerformanceRow> recent = all.stream()
                .sorted(Comparator.comparing(PerformanceData.PerformanceRow::getDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(0, recentRows))
                .collect(Collectors.toList());

        Map<String, Object> out = new HashMap<>();
        out.put("rowCount", all.size());
        out.put("totalImpressions", impressions);
        out.put("totalClicks", clicks);
        out.put("totalCost", Math.round(cost * 100) / 100.0);
        out.put("totalConversions", conversions);
        out.put("ctr", Math.round(ctr * 10000) / 10000.0);
        out.put("roi", Math.round(roi * 100) / 100.0);
        out.put("recentRows", recent);
        return out;
    }

    /**
     * 按素材聚合效果，筛选最近 {@code days} 天；可选限定计划、广告组。
     * 按 ROI 降序、再按展示量降序，取前 {@code limit} 条；过滤展示量低于 {@code minImpressions} 的噪点。
     */
    public List<CreativePerformanceScore> topCreativesByPerformance(
            String userId,
            String campaignId,
            String adGroupId,
            int days,
            int limit,
            long minImpressions) {
        int d = Math.max(1, Math.min(days, 365));
        int lim = Math.max(1, Math.min(limit, 50));
        LocalDate cutoff = LocalDate.now().minusDays(d);
        String cutoffStr = cutoff.toString();

        List<PerformanceData.PerformanceRow> rows = loadPerformance(userId).getData();
        Map<String, Agg> map = new LinkedHashMap<>();
        for (PerformanceData.PerformanceRow r : rows) {
            if (r.getCreativeId() == null || r.getCreativeId().isBlank()) {
                continue;
            }
            if (r.getDate() == null || r.getDate().compareTo(cutoffStr) < 0) {
                continue;
            }
            if (campaignId != null && !campaignId.isBlank() && !campaignId.equals(r.getCampaignId())) {
                continue;
            }
            if (adGroupId != null && !adGroupId.isBlank() && !adGroupId.equals(r.getAdGroupId())) {
                continue;
            }
            String cid = r.getCreativeId().trim();
            Agg a = map.computeIfAbsent(cid, k -> new Agg());
            a.impressions += r.getImpressions();
            a.clicks += r.getClicks();
            a.cost += r.getCost();
            a.conversions += r.getConversions();
        }

        List<CreativePerformanceScore> list = new ArrayList<>();
        for (Map.Entry<String, Agg> e : map.entrySet()) {
            Agg a = e.getValue();
            if (a.impressions < minImpressions) {
                continue;
            }
            CreativePerformanceScore s = new CreativePerformanceScore();
            s.setCreativeId(e.getKey());
            s.setImpressions(a.impressions);
            s.setClicks(a.clicks);
            s.setCost(Math.round(a.cost * 100) / 100.0);
            s.setConversions(a.conversions);
            double ctr = a.impressions > 0 ? (double) a.clicks / a.impressions : 0.0;
            double roi = a.cost > 0 ? a.conversions * 10.0 / a.cost : 0.0;
            s.setCtr(Math.round(ctr * 10000) / 10000.0);
            s.setRoi(Math.round(roi * 10000) / 10000.0);
            list.add(s);
        }
        list.sort(Comparator
                .comparingDouble(CreativePerformanceScore::getRoi).reversed()
                .thenComparingLong(CreativePerformanceScore::getImpressions).reversed());
        if (list.size() > lim) {
            return new ArrayList<>(list.subList(0, lim));
        }
        return list;
    }

    private static final class Agg {
        long impressions;
        long clicks;
        double cost;
        long conversions;
    }
}
