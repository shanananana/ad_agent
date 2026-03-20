package com.example.adagent.bidding;

import com.example.adagent.bidding.dto.BaseBidModelFile;
import com.example.adagent.bidding.dto.CoefficientsFile;
import com.example.adagent.bidding.dto.EffectSnapshotFile;
import com.example.adagent.config.BiddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 自动调价助手：根据效果快照建议新 α，并做护栏（区间 + 单周期相对变化）。
 */
@Service
public class BidCoefficientLlmService {

    private static final Logger logger = LoggerFactory.getLogger(BidCoefficientLlmService.class);
    private final ChatClient biddingChatClient;
    private final BiddingProperties biddingProperties;
    private final ObjectMapper objectMapper;

    public BidCoefficientLlmService(
            @Qualifier("biddingChatClient") ChatClient biddingChatClient,
            BiddingProperties biddingProperties,
            ObjectMapper objectMapper) {
        this.biddingChatClient = biddingChatClient;
        this.biddingProperties = biddingProperties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * @return 新系数文件（version 未递增，由调用方处理）；失败返回 null
     */
    public LlmCoefficientResult suggestCoefficients(
            BaseBidModelFile baseModel,
            CoefficientsFile currentCoeffs,
            EffectSnapshotFile snapshot) {
        return suggestCoefficients(baseModel, currentCoeffs, snapshot, null, null);
    }

    /**
     * @param campaignId   非空时写入 prompt，便于 rationale 对齐计划
     * @param campaignName 可选展示名
     */
    public LlmCoefficientResult suggestCoefficients(
            BaseBidModelFile baseModel,
            CoefficientsFile currentCoeffs,
            EffectSnapshotFile snapshot,
            String campaignId,
            String campaignName) {
        String userPrompt = buildUserPrompt(baseModel, currentCoeffs, snapshot, campaignId, campaignName);
        String raw;
        try {
            raw = biddingChatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            logger.error("【自动调价】LLM 调用失败", e);
            return null;
        }
        if (raw == null || raw.isBlank()) {
            logger.warn("【自动调价】LLM 返回空");
            return null;
        }
        String json = extractJsonObject(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            String rationale = root.has("rationale") ? root.get("rationale").asText("") : "";
            Map<String, Double> suggested = new HashMap<>();
            if (root.has("entries") && root.get("entries").isArray()) {
                for (JsonNode n : root.get("entries")) {
                    if (!n.has("audience") || !n.has("hourSlot") || !n.has("device") || !n.has("alpha")) {
                        continue;
                    }
                    String key = key(n.get("audience").asText(), n.get("hourSlot").asInt(), n.get("device").asText());
                    suggested.put(key, n.get("alpha").asDouble());
                }
            }
            CoefficientsFile merged = applyGuards(currentCoeffs, suggested);
            return new LlmCoefficientResult(merged, rationale, raw);
        } catch (Exception e) {
            logger.warn("【自动调价】解析 LLM JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private static String key(String audience, int hour, String device) {
        return audience + "|" + hour + "|" + device;
    }

    private CoefficientsFile applyGuards(CoefficientsFile current, Map<String, Double> suggested) {
        double min = biddingProperties.getAlphaMin();
        double max = biddingProperties.getAlphaMax();
        double rel = biddingProperties.getMaxRelativeChange();
        CoefficientsFile out = new CoefficientsFile();
        out.setVersion(current.getVersion());
        out.setUpdatedAt(current.getUpdatedAt());
        out.setEntries(new java.util.ArrayList<>());
        if (current.getEntries() == null) {
            return out;
        }
        for (CoefficientsFile.CoefficientEntry e : current.getEntries()) {
            CoefficientsFile.CoefficientEntry copy = new CoefficientsFile.CoefficientEntry();
            copy.setAudience(e.getAudience());
            copy.setHourSlot(e.getHourSlot());
            copy.setDevice(e.getDevice());
            String k = key(e.getAudience(), e.getHourSlot(), e.getDevice());
            double oldA = e.getAlpha();
            double next = suggested.getOrDefault(k, oldA);
            next = Math.max(min, Math.min(max, next));
            double low = oldA * (1.0 - rel);
            double high = oldA * (1.0 + rel);
            next = Math.max(low, Math.min(high, next));
            copy.setAlpha(Math.round(next * 10000) / 10000.0);
            out.getEntries().add(copy);
        }
        return out;
    }

    private static String extractJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String buildUserPrompt(
            BaseBidModelFile base,
            CoefficientsFile coeffs,
            EffectSnapshotFile snap,
            String campaignId,
            String campaignName) {
        String window = snap.getWindowEnd() != null ? snap.getWindowEnd() : "";
        StringBuilder sb = new StringBuilder();
        if (campaignId != null && !campaignId.isBlank()) {
            String namePart =
                    (campaignName != null && !campaignName.isBlank()) ? ("，名称：" + campaignName) : "";
            sb.append("【当前投放计划】计划 ID：")
                    .append(campaignId)
                    .append(namePart)
                    .append("。以下 B×α 维度数据仅针对该计划在策略层的出价系数网格。\n\n");
        }
        sb.append(
                """
                你是「自动调价助手」。输入中每一行维度包含：
                - B（baseBid）：基础出价，由人工/策略事先设定。
                - alpha：当前系数；生效侧可理解为在 B 上再乘 alpha（本任务只调整 alpha）。
                - impressions / clicks / cost / ctr / roi：该窗口内效果；缺省字段表示无快照数据。

                请结合 ROI、CTR、消耗与量级，给出新的 alpha：表现偏弱（如 ROI 明显偏低、CTR 异常低、空耗）的维度适度下调；表现稳健或偏强的可略上调。变化须平滑，避免剧烈跳变。

                rationale 字段（中文）必须写清「谁涨了、谁降了、为什么」，严格按下面结构组织，小节标题原样保留，小节内每条一行，使用换行符 \\n 拼接为一条 JSON 字符串：
                ### 上调
                - 人群/时段h/设备：旧alpha→新alpha，原因（必须点名具体指标，如 ROI、CTR、消耗）
                （若无上调则写：- 无）
                ### 下调
                - 人群/时段h/设备：旧alpha→新alpha，原因（同上）
                （若无下调则写：- 无）
                ### 维持与小结
                - 一句话概括整体策略；未出现在上/下调列表中的维度视为基本维持，可说明为何不动。

                另须输出 entries：与下方数据行一一对应，每条含 audience、hourSlot、device、alpha（你建议的新系数）。

                效果窗口结束: """);
        sb.append(window);
        sb.append(

                """

                输出仅为一个 JSON 对象，不要 Markdown 代码围栏，不要其它前后缀。示例（结构示意，数值勿照抄）：
                {"rationale":"### 上调\\n- …\\n### 下调\\n- …\\n### 维持与小结\\n- …","entries":[{"audience":"18-24","hourSlot":0,"device":"ios","alpha":1.0}]}

                【当前数据 JSON】
                """);
        try {
            java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
            java.util.Map<String, EffectSnapshotFile.EffectSnapshotEntry> snapMap = new java.util.HashMap<>();
            if (snap.getEntries() != null) {
                for (EffectSnapshotFile.EffectSnapshotEntry se : snap.getEntries()) {
                    snapMap.put(key(se.getAudience(), se.getHourSlot(), se.getDevice()), se);
                }
            }
            if (coeffs.getEntries() != null) {
                for (CoefficientsFile.CoefficientEntry ce : coeffs.getEntries()) {
                    BaseBidModelFile.BaseBidEntry be = findBase(base, ce.getAudience(), ce.getHourSlot(), ce.getDevice());
                    EffectSnapshotFile.EffectSnapshotEntry se =
                            snapMap.get(key(ce.getAudience(), ce.getHourSlot(), ce.getDevice()));
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("audience", ce.getAudience());
                    row.put("hourSlot", ce.getHourSlot());
                    row.put("device", ce.getDevice());
                    row.put("baseBid", be != null ? be.getBaseBid() : 1.0);
                    row.put("alpha", ce.getAlpha());
                    if (se != null) {
                        row.put("impressions", se.getImpressions());
                        row.put("clicks", se.getClicks());
                        row.put("cost", se.getCost());
                        row.put("ctr", se.getCtr());
                        row.put("roi", se.getRoi());
                    }
                    rows.add(row);
                }
            }
            sb.append(objectMapper.writeValueAsString(rows));
        } catch (Exception e) {
            sb.append("(序列化输入失败) ");
        }
        return sb.toString();
    }

    private static BaseBidModelFile.BaseBidEntry findBase(BaseBidModelFile base, String aud, int h, String dev) {
        if (base.getEntries() == null) {
            return null;
        }
        for (BaseBidModelFile.BaseBidEntry e : base.getEntries()) {
            if (aud.equals(e.getAudience()) && e.getHourSlot() == h && dev.equals(e.getDevice())) {
                return e;
            }
        }
        return null;
    }

    public record LlmCoefficientResult(CoefficientsFile newCoefficients, String rationale, String rawLlmResponse) {
    }
}
