package com.example.adagent.creative;

import com.example.adagent.creative.dto.SuggestPromptRequest;
import com.example.adagent.creative.dto.SuggestPromptResponse;
import com.example.adagent.data.ContentCatalogRepository;
import com.example.adagent.data.GlobalCreativeRepository;
import com.example.adagent.data.PerformanceDataRepository;
import com.example.adagent.data.dto.ContentItem;
import com.example.adagent.data.dto.CreativePerformanceScore;
import com.example.adagent.data.dto.GlobalCreative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 结合投放效果较好的素材文案、版位画幅说明、内容库摘要与用户草稿，通过 {@code PromptTemplate} 拼装后调用 LLM。
 */
@Service
public class CreativePromptSuggestService {

    private static final Logger logger = LoggerFactory.getLogger(CreativePromptSuggestService.class);
    private static final int PROMPT_OUT_MAX = 2000;
    private static final int SNAPSHOT_MAX = 400;

    private final PromptTemplate creativeSuggestUserTemplate;
    private final ChatClient creativePromptChatClient;
    private final PerformanceDataRepository performanceDataRepository;
    private final GlobalCreativeRepository globalCreativeRepository;
    private final ContentCatalogRepository contentCatalogRepository;

    public CreativePromptSuggestService(
            @Qualifier("creativePromptChatClient") ChatClient creativePromptChatClient,
            PerformanceDataRepository performanceDataRepository,
            GlobalCreativeRepository globalCreativeRepository,
            ContentCatalogRepository contentCatalogRepository) {
        this.creativePromptChatClient = creativePromptChatClient;
        this.performanceDataRepository = performanceDataRepository;
        this.globalCreativeRepository = globalCreativeRepository;
        this.contentCatalogRepository = contentCatalogRepository;
        this.creativeSuggestUserTemplate = new PromptTemplate("""
                ## 版位与建议画幅（须在画面描述中体现构图与比例意向）
                {placementSpec}

                ## 高表现素材与文案参考（按 ROI 排序，含目录标题/描述/历史画面描述片段）
                {performanceReference}

                ## 当前推广内容（内容库）
                {contentLibrary}

                ## 用户补充 / 草稿（画面意图、必须元素、禁忌等）
                {userNotes}

                请综合以上各块，写出完整、具体、可直接用于文生图模型的中文「画面描述」。
                格式要求：按语义换行——例如「整体构图与比例」「主体与场景」「光线与色彩」「留白与安全区」「须避免的元素」各为意群，意群之间换行；相关句子可单独成行；大段之间可用一个空行分段。不要使用「###」标题、不要用「-」条列、不要编号列表。
                内容要求：写清主体、场景、光线、色调、构图与留白；遵守上文版位中的宽高比与安全区建议；若有禁忌用否定句写入正文。不要解释创作理由。
                """);
    }

    public SuggestPromptResponse suggest(SuggestPromptRequest req) {
        if (req == null || !StringUtils.hasText(req.getUserId())) {
            return SuggestPromptResponse.error("userId 不能为空");
        }
        String userId = req.getUserId().trim();
        int days = req.getDays() != null ? req.getDays() : 30;
        int topK = req.getTopK() != null ? req.getTopK() : 8;

        String campaignId = StringUtils.hasText(req.getCampaignId()) ? req.getCampaignId().trim() : null;
        String adGroupId = StringUtils.hasText(req.getAdGroupId()) ? req.getAdGroupId().trim() : null;

        List<CreativePerformanceScore> ranked = performanceDataRepository.topCreativesByPerformance(
                userId, campaignId, adGroupId, days, topK, 100L);

        List<String> refIds = ranked.stream().map(CreativePerformanceScore::getCreativeId).collect(Collectors.toList());
        var creativeMap = globalCreativeRepository.findByIds(userId, refIds);

        String performanceReference = buildPerformanceReference(ranked, creativeMap, days);
        String contentLibrary = buildContentLibrary(userId, req.getContentId());
        String userNotes = StringUtils.hasText(req.getCurrentDescription())
                ? req.getCurrentDescription().trim()
                : "（用户未提供草稿。）";
        String placementSpec = CreativePlacementPromptSpec.build(req.getPlacement());

        Prompt prompt = creativeSuggestUserTemplate.create(Map.of(
                "placementSpec", placementSpec,
                "performanceReference", performanceReference,
                "contentLibrary", contentLibrary,
                "userNotes", userNotes));

        String raw;
        try {
            raw = creativePromptChatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            logger.error("【文生图 prompt 推荐】LLM 调用失败", e);
            return SuggestPromptResponse.error("生成失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        if (!StringUtils.hasText(raw)) {
            return SuggestPromptResponse.error("模型返回为空");
        }
        String cleaned = normalizeNewlines(cleanLlmOutput(raw.trim()));
        if (cleaned.length() > PROMPT_OUT_MAX) {
            cleaned = cleaned.substring(0, PROMPT_OUT_MAX);
        }
        return SuggestPromptResponse.ok(cleaned, refIds);
    }

    private String buildPerformanceReference(
            List<CreativePerformanceScore> ranked,
            java.util.Map<String, GlobalCreative> creativeMap,
            int days) {
        StringBuilder perfBlock = new StringBuilder();
        if (ranked.isEmpty()) {
            perfBlock.append("（最近 ").append(days).append(" 天内无足够投放样本，或展示量未达阈值；无高表现素材可参考。）\n");
            return perfBlock.toString();
        }
        perfBlock.append("以下素材按 ROI 排序，并附目录中的标题/描述/历史 prompt 片段：\n\n");
        int i = 1;
        for (CreativePerformanceScore s : ranked) {
            GlobalCreative gc = creativeMap.get(s.getCreativeId());
            perfBlock.append(i++).append(". 素材ID: ").append(s.getCreativeId()).append("\n");
            perfBlock.append("   效果: 展示=").append(s.getImpressions())
                    .append(" 点击=").append(s.getClicks())
                    .append(" 消耗≈").append(s.getCost())
                    .append(" 转化=").append(s.getConversions())
                    .append(" CTR=").append(s.getCtr())
                    .append(" ROI=").append(s.getRoi()).append("\n");
            if (gc != null) {
                if (StringUtils.hasText(gc.getTitle())) {
                    perfBlock.append("   标题: ").append(gc.getTitle()).append("\n");
                }
                if (StringUtils.hasText(gc.getDescription())) {
                    perfBlock.append("   描述: ").append(gc.getDescription()).append("\n");
                }
                if (StringUtils.hasText(gc.getPromptSnapshot())) {
                    perfBlock.append("   曾用画面描述: ")
                            .append(truncate(gc.getPromptSnapshot(), SNAPSHOT_MAX)).append("\n");
                }
            } else {
                perfBlock.append("   （目录中暂无该素材详情）\n");
            }
            perfBlock.append("\n");
        }
        return perfBlock.toString();
    }

    private String buildContentLibrary(String userId, String contentId) {
        StringBuilder contentBlock = new StringBuilder();
        if (StringUtils.hasText(contentId)) {
            ContentItem c = contentCatalogRepository.findById(userId, contentId.trim()).orElse(null);
            if (c != null) {
                contentBlock.append("名称: ").append(nullToEmpty(c.getName())).append("\n");
                contentBlock.append("摘要: ").append(nullToEmpty(c.getSummary())).append("\n");
            } else {
                contentBlock.append("（未找到 contentId=").append(contentId).append(" 的内容条目）\n");
            }
        } else {
            contentBlock.append("（未指定内容库条目。）\n");
        }
        return contentBlock.toString();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s != null ? s : "";
        }
        return s.substring(0, max) + "…";
    }

    /** 统一换行符，避免过多空行 */
    private static String normalizeNewlines(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String t = s.replace("\r\n", "\n").replace('\r', '\n');
        return t.replaceAll("\n{4,}", "\n\n\n");
    }

    private static String cleanLlmOutput(String s) {
        String t = s;
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
            }
            int end = t.lastIndexOf("```");
            if (end > 0) {
                t = t.substring(0, end);
            }
        }
        t = t.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("「") && t.endsWith("」"))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}
