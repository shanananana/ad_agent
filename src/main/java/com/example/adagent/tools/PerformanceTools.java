package com.example.adagent.tools;

import com.example.adagent.data.PerformanceDataRepository;
import com.example.adagent.data.dto.PerformanceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring AI <strong>效果查询工具</strong>：读本地 {@code performance.json}，支持按计划/广告组/广告/素材、渠道、年龄段与时间窗过滤，
 * 返回汇总与按日序列，供对话中回答「最近效果」「分日趋势」等问题。
 */
@Component
public class PerformanceTools {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTools.class);
    private final PerformanceDataRepository performanceDataRepository;

    public PerformanceTools(PerformanceDataRepository performanceDataRepository) {
        this.performanceDataRepository = performanceDataRepository;
    }

    @Tool(description = "查询投放效果：展示量、点击量、点击率CTR、消耗、转化、ROI。返回「汇总」+「每日数据」便于分析。可选参数：userId 当前用户ID（多用户时必传以隔离数据）；campaignId 按计划筛选；channel 按渠道如 APP/H5；ageRange 按年龄段如 18-24/25-34；days 最近几天，默认7。分析计划时请使用本工具并依据返回的每日数据说明趋势。")
    public String queryPerformance(
            String userId,
            String campaignId,
            String level,
            String channel,
            String ageRange,
            Integer days) {
        try {
            PerformanceData data = performanceDataRepository.loadPerformance(userId);
            if (data.getData() == null || data.getData().isEmpty()) {
                return "暂无效果数据。请先「加一个投放计划」或进行策略调整，系统会自动生成效果数据。";
            }
            List<PerformanceData.PerformanceRow> list = data.getData();
            if (campaignId != null && !campaignId.trim().isEmpty()) {
                list = list.stream().filter(r -> campaignId.trim().equals(r.getCampaignId())).collect(Collectors.toList());
            }
            if (channel != null && !channel.trim().isEmpty()) {
                list = list.stream().filter(r -> channel.trim().equalsIgnoreCase(r.getChannel())).collect(Collectors.toList());
            }
            if (ageRange != null && !ageRange.trim().isEmpty()) {
                list = list.stream().filter(r -> ageRange.trim().equals(r.getAgeRange())).collect(Collectors.toList());
            }
            int dayLimit = (days != null && days > 0) ? days : 7;
            java.time.LocalDate today = java.time.LocalDate.now();
            String cutoffStart = today.minusDays(dayLimit).toString();
            String cutoffEnd = today.toString();
            final String start = cutoffStart;
            final String end = cutoffEnd;
            list = list.stream().filter(r -> r.getDate() != null
                    && r.getDate().compareTo(start) >= 0
                    && r.getDate().compareTo(end) <= 0).collect(Collectors.toList());

            if (list.isEmpty()) {
                List<PerformanceData.PerformanceRow> allData = data.getData();
                if (allData != null && !allData.isEmpty()) {
                    performanceDataRepository.generateAllAndOverwrite(userId);
                    data = performanceDataRepository.loadPerformance(userId);
                    list = data.getData() != null ? data.getData() : List.of();
                    if (campaignId != null && !campaignId.trim().isEmpty()) {
                        list = list.stream().filter(r -> campaignId.trim().equals(r.getCampaignId())).collect(Collectors.toList());
                    }
                    if (channel != null && !channel.trim().isEmpty()) {
                        list = list.stream().filter(r -> channel.trim().equalsIgnoreCase(r.getChannel())).collect(Collectors.toList());
                    }
                    if (ageRange != null && !ageRange.trim().isEmpty()) {
                        list = list.stream().filter(r -> ageRange.trim().equals(r.getAgeRange())).collect(Collectors.toList());
                    }
                    list = list.stream().filter(r -> r.getDate() != null
                            && r.getDate().compareTo(start) >= 0
                            && r.getDate().compareTo(end) <= 0).collect(Collectors.toList());
                }
                if (list.isEmpty()) {
                    return "在给定筛选条件下暂无效果数据。";
                }
            }

            long impressions = list.stream().mapToLong(PerformanceData.PerformanceRow::getImpressions).sum();
            long clicks = list.stream().mapToLong(PerformanceData.PerformanceRow::getClicks).sum();
            double cost = list.stream().mapToDouble(PerformanceData.PerformanceRow::getCost).sum();
            long conversions = list.stream().mapToLong(PerformanceData.PerformanceRow::getConversions).sum();
            double ctr = impressions > 0 ? (double) clicks / impressions : 0;
            double roi = cost > 0 ? (conversions * 10.0) / cost : 0;

            StringBuilder sb = new StringBuilder();
            sb.append("【投放效果汇总】最近 ").append(dayLimit).append(" 天（").append(start).append(" 至 ").append(end).append("，截至今日）\n\n");
            sb.append("- 展示量：").append(impressions).append("\n");
            sb.append("- 点击量：").append(clicks).append("\n");
            sb.append("- 点击率(CTR)：").append(String.format("%.2f%%", ctr * 100)).append("\n");
            sb.append("- 消耗：").append(String.format("%.2f 元", cost)).append("\n");
            sb.append("- 转化数：").append(conversions).append("\n");
            sb.append("- ROI：").append(String.format("%.2f", roi)).append("\n\n");

            Map<String, List<PerformanceData.PerformanceRow>> byDate = list.stream()
                    .collect(Collectors.groupingBy(PerformanceData.PerformanceRow::getDate));
            sb.append("【每日数据】\n\n");
            byDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .forEach(entry -> {
                        String date = entry.getKey();
                        List<PerformanceData.PerformanceRow> rows = entry.getValue();
                        long dImp = rows.stream().mapToLong(PerformanceData.PerformanceRow::getImpressions).sum();
                        long dClicks = rows.stream().mapToLong(PerformanceData.PerformanceRow::getClicks).sum();
                        double dCost = rows.stream().mapToDouble(PerformanceData.PerformanceRow::getCost).sum();
                        long dConv = rows.stream().mapToLong(PerformanceData.PerformanceRow::getConversions).sum();
                        double dCtr = dImp > 0 ? (double) dClicks / dImp : 0;
                        double dRoi = dCost > 0 ? (dConv * 10.0) / dCost : 0;
                        sb.append("  日期 ").append(date).append("：展示 ").append(dImp)
                                .append("，点击 ").append(dClicks)
                                .append("，CTR ").append(String.format("%.2f%%", dCtr * 100))
                                .append("，消耗 ").append(String.format("%.2f 元", dCost))
                                .append("，转化 ").append(dConv)
                                .append("，ROI ").append(String.format("%.2f", dRoi)).append("\n");
                    });

            return sb.toString();
        } catch (Exception e) {
            logger.error("queryPerformance 失败", e);
            return "查询效果失败：" + e.getMessage();
        }
    }
}
