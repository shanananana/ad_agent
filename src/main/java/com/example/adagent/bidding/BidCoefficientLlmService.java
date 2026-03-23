package com.example.adagent.bidding;

import com.example.adagent.bidding.dto.BaseBidModelFile;
import com.example.adagent.bidding.dto.CoefficientsFile;
import com.example.adagent.bidding.dto.EffectSnapshotFile;
import com.example.adagent.config.BiddingProperties;
import com.example.adagent.prompt.ClasspathPromptLoader;
import com.example.adagent.prompt.PromptResourcePaths;
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
 * 自动调价 <strong>LLM 推理</strong>：将效果快照摘要交给无工具的 {@code biddingChatClient}，解析 JSON 得到各维度新 α，
 * 并施加 {@code alpha-min/max} 与单周期最大相对变化等护栏后再写回领域模型。
 */
@Service
public class BidCoefficientLlmService {

    private static final Logger logger = LoggerFactory.getLogger(BidCoefficientLlmService.class);
    private final ChatClient biddingChatClient;
    private final BiddingProperties biddingProperties;
    private final ObjectMapper objectMapper;
    private final ClasspathPromptLoader classpathPromptLoader;

    public BidCoefficientLlmService(
            @Qualifier("biddingChatClient") ChatClient biddingChatClient,
            BiddingProperties biddingProperties,
            ObjectMapper objectMapper,
            ClasspathPromptLoader classpathPromptLoader) {
        this.biddingChatClient = biddingChatClient;
        this.biddingProperties = biddingProperties;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.classpathPromptLoader = classpathPromptLoader;
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
        String planIntroSection = "";
        if (campaignId != null && !campaignId.isBlank()) {
            String nameClause =
                    (campaignName != null && !campaignName.isBlank()) ? "，名称：" + campaignName : "";
            planIntroSection = classpathPromptLoader.renderTemplate(
                    PromptResourcePaths.BIDDING_PLAN_INTRO,
                    Map.of("campaignId", campaignId.trim(), "nameClause", nameClause));
        }
        String jsonExampleBlock = classpathPromptLoader.loadText(PromptResourcePaths.BIDDING_JSON_EXAMPLE);
        String dataJson;
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
            dataJson = objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            dataJson = "(序列化输入失败) ";
        }
        return classpathPromptLoader.renderTemplate(
                PromptResourcePaths.BIDDING_COEFFICIENT_USER,
                Map.of(
                        "planIntroSection", planIntroSection,
                        "effectWindowEnd", window,
                        "jsonExampleBlock", jsonExampleBlock,
                        "dataJson", dataJson));
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
