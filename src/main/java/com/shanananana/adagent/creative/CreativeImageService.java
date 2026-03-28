package com.shanananana.adagent.creative;

import com.shanananana.adagent.config.DataPathConfig;
import com.shanananana.adagent.creative.dto.GenerateImageRequest;
import com.shanananana.adagent.creative.dto.GenerateImageResponse;
import com.shanananana.adagent.data.GlobalCreativeRepository;
import com.shanananana.adagent.data.dto.GlobalCreative;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * <strong>文生图执行服务</strong>：封装 Spring AI {@link ImageModel}（默认通义万相），
 * 将前端描述转为图片并可选落盘到 {@code data/creative/assets/{userId}/}；模型名等见 {@code spring.ai.dashscope.image.options.model}。
 */
@Service
public class CreativeImageService {

    /** 与 DashScope 文档建议的 prompt 上限量级一致，避免过长被拒 */
    private static final int PROMPT_MAX_LEN = 2000;
    private static final int TITLE_MAX_LEN = 200;

    private final ObjectProvider<ImageModel> imageModelProvider;
    private final DataPathConfig dataPathConfig;
    private final GlobalCreativeRepository globalCreativeRepository;

    public CreativeImageService(ObjectProvider<ImageModel> imageModelProvider,
                                  DataPathConfig dataPathConfig,
                                  GlobalCreativeRepository globalCreativeRepository) {
        this.imageModelProvider = imageModelProvider;
        this.dataPathConfig = dataPathConfig;
        this.globalCreativeRepository = globalCreativeRepository;
    }

    public GenerateImageResponse generate(GenerateImageRequest request) {
        if (request == null || !StringUtils.hasText(request.getPrompt())) {
            return GenerateImageResponse.error("描述不能为空");
        }
        if (request.isPersist()) {
            if (!StringUtils.hasText(request.getUserId())) {
                return GenerateImageResponse.error("持久化时需传 userId");
            }
            return generateAndPersist(
                    request.getUserId().trim(),
                    request.getPrompt().trim(),
                    request.getContentId(),
                    request.getPlacement(),
                    request.getTitle());
        }
        return generate(request.getPrompt().trim());
    }

    public GenerateImageResponse generate(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return GenerateImageResponse.error("描述不能为空");
        }
        String trimmed = prompt.trim();
        if (trimmed.length() > PROMPT_MAX_LEN) {
            return GenerateImageResponse.error("描述过长，请控制在 " + PROMPT_MAX_LEN + " 字以内");
        }

        ImageModel model = imageModelProvider.getIfAvailable();
        if (model == null) {
            return GenerateImageResponse.error(
                    "图片生成未启用：请在 application.yml 设置 spring.ai.model.image=dashscope，并配置 spring.ai.dashscope.api-key");
        }

        try {
            var response = model.call(new ImagePrompt(trimmed));
            List<ImageGeneration> results = response.getResults();
            if (results == null || results.isEmpty()) {
                return GenerateImageResponse.error("模型未返回图片，请稍后重试");
            }
            Image out = results.get(0).getOutput();
            if (out == null) {
                return GenerateImageResponse.error("模型未返回图片，请稍后重试");
            }
            return GenerateImageResponse.ok(out.getUrl(), out.getB64Json());
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return GenerateImageResponse.error("生成失败：" + msg);
        }
    }

    private GenerateImageResponse generateAndPersist(String userId, String prompt, String contentId, String placement,
                                                     String title) {
        GenerateImageResponse base = generate(prompt);
        if (!"ok".equals(base.getStatus())) {
            return base;
        }
        if (!StringUtils.hasText(base.getB64Json())) {
            return GenerateImageResponse.error("持久化失败：响应中无 base64 图片数据，无法写入本地");
        }
        String id = GlobalCreativeRepository.newCreativeId();
        String safeUser = DataPathConfig.sanitizeId(userId);
        Path dir = dataPathConfig.getCreativeAssetsRoot().resolve(safeUser);
        try {
            Files.createDirectories(dir);
            String filename = id + ".png";
            Path file = dir.resolve(filename);
            byte[] bytes = Base64.getDecoder().decode(base.getB64Json().trim());
            Files.write(file, bytes);
        } catch (IllegalArgumentException e) {
            return GenerateImageResponse.error("持久化失败：base64 解码无效");
        } catch (Exception e) {
            return GenerateImageResponse.error("持久化失败：" + e.getMessage());
        }
        String relPath = "creative/assets/" + safeUser + "/" + id + ".png";
        GlobalCreative gc = new GlobalCreative();
        gc.setId(id);
        gc.setType("IMAGE");
        gc.setTitle(resolveTitle(title, prompt));
        gc.setStatus("APPROVED");
        gc.setImagePath(relPath);
        gc.setPromptSnapshot(prompt);
        if (StringUtils.hasText(contentId)) {
            gc.setContentId(contentId.trim());
        }
        if (StringUtils.hasText(placement)) {
            gc.setPlacement(placement.trim());
        }
        gc.setTaskStatus("done");
        globalCreativeRepository.upsert(userId, gc);
        base.setCreativeId(id);
        base.setPersistedPath(relPath);
        return base;
    }

    private static String resolveTitle(String title, String prompt) {
        if (StringUtils.hasText(title)) {
            String t = title.trim();
            return t.length() > TITLE_MAX_LEN ? t.substring(0, TITLE_MAX_LEN) : t;
        }
        String p = prompt.trim();
        return p.length() > TITLE_MAX_LEN ? p.substring(0, TITLE_MAX_LEN) : p;
    }
}
