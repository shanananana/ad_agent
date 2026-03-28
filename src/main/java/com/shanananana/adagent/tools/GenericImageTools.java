package com.shanananana.adagent.tools;

import com.shanananana.adagent.creative.CreativeImageService;
import com.shanananana.adagent.creative.MaterialCreativeToolCapture;
import com.shanananana.adagent.creative.dto.GenerateImageRequest;
import com.shanananana.adagent.creative.dto.GenerateImageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用文生图工具：只接收已整理好的 {@code prompt}，由模型在调用前结合 {@link com.shanananana.adagent.tools.BaseDataTools} 与技能说明自行组织描述。
 */
@Component
public class GenericImageTools {

    private static final Logger log = LoggerFactory.getLogger(GenericImageTools.class);

    private final CreativeImageService creativeImageService;
    private final ObjectMapper objectMapper;

    public GenericImageTools(CreativeImageService creativeImageService, ObjectMapper objectMapper) {
        this.creativeImageService = creativeImageService;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "通用文生图：传入已整理好的画面描述正文 prompt 即可出图。"
            + "若无现成描述，须先通过 queryBaseData 等了解计划/素材上下文，自行在推理中写好完整 prompt 再调用本工具。"
            + "参数：prompt 必填；persist 为 true 时必须传 userId；title、contentId、placement 可选。")
    public String generateImageFromPrompt(
            String prompt,
            Boolean persist,
            String userId,
            String title,
            String contentId,
            String placement) {
        if (!StringUtils.hasText(prompt)) {
            return jsonError("prompt 不能为空");
        }
        boolean doPersist = Boolean.TRUE.equals(persist);
        if (doPersist && !StringUtils.hasText(userId)) {
            return jsonError("persist 为 true 时必须提供 userId");
        }
        GenerateImageRequest req = new GenerateImageRequest();
        req.setPrompt(prompt.trim());
        req.setPersist(doPersist);
        if (StringUtils.hasText(userId)) {
            req.setUserId(userId.trim());
        }
        req.setTitle(trimOrNull(title));
        req.setContentId(trimOrNull(contentId));
        req.setPlacement(trimOrNull(placement));
        GenerateImageResponse out = creativeImageService.generate(req);
        String json = toImageToolJson(out);
        MaterialCreativeToolCapture.tryStoreGenerateJson(json);
        return json;
    }

    private String toImageToolJson(GenerateImageResponse out) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", out.getStatus());
            m.put("message", out.getMessage());
            if ("ok".equals(out.getStatus())) {
                m.put("imageUrl", out.getImageUrl());
                m.put("creativeId", out.getCreativeId());
                m.put("persistedPath", out.getPersistedPath());
                m.put("note", "b64Json 已省略以节省上下文");
            }
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            log.error("generateImageFromPrompt 序列化失败", e);
            return jsonError(e.getMessage());
        }
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private String jsonError(String msg) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", "error", "message", msg != null ? msg : ""));
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"message\":\"serialize failed\"}";
        }
    }
}
