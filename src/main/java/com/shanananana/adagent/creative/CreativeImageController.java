package com.shanananana.adagent.creative;

import com.shanananana.adagent.creative.dto.GenerateImageRequest;
import com.shanananana.adagent.creative.dto.GenerateImageResponse;
import com.shanananana.adagent.creative.dto.MaterialCreativeRequest;
import com.shanananana.adagent.creative.dto.MaterialCreativeResponse;
import com.shanananana.adagent.creative.dto.SuggestPromptRequest;
import com.shanananana.adagent.creative.dto.SuggestPromptResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <strong>创意文生图 REST</strong>：文生图与「智能生成描述」等接口，委托 {@link CreativeImageService}、
 * {@link CreativePromptSuggestService}；与对话编排、自动调价模块边界清晰。
 * <p>素材 Tab「技能一键」走 {@link MaterialCreativeAgentService}：仅传结构化 JSON 给主 {@code ChatClient}，由模型与技能驱动工具。</p>
 */
@RestController
@RequestMapping("/api/ad-agent/creative")
public class CreativeImageController {

    private final CreativeImageService creativeImageService;
    private final CreativePromptSuggestService creativePromptSuggestService;
    private final MaterialCreativeAgentService materialCreativeAgentService;

    public CreativeImageController(CreativeImageService creativeImageService,
                                   CreativePromptSuggestService creativePromptSuggestService,
                                   MaterialCreativeAgentService materialCreativeAgentService) {
        this.creativeImageService = creativeImageService;
        this.creativePromptSuggestService = creativePromptSuggestService;
        this.materialCreativeAgentService = materialCreativeAgentService;
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateImageResponse> generate(@RequestBody(required = false) GenerateImageRequest body) {
        if (body == null || body.getPrompt() == null) {
            return ResponseEntity.badRequest()
                    .body(GenerateImageResponse.error("请求体需包含 prompt 字段"));
        }
        GenerateImageResponse out = creativeImageService.generate(body);
        if ("ok".equals(out.getStatus())) {
            return ResponseEntity.ok(out);
        }
        if (out.getMessage() != null && out.getMessage().contains("未启用")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(out);
        }
        return ResponseEntity.badRequest().body(out);
    }

    /**
     * 根据高 ROI 素材文案 + 内容库摘要 + 用户草稿，生成推荐画面描述（耗 LLM token）。
     */
    @PostMapping("/suggest-prompt")
    public ResponseEntity<SuggestPromptResponse> suggestPrompt(@RequestBody(required = false) SuggestPromptRequest body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(SuggestPromptResponse.error("请求体不能为空"));
        }
        SuggestPromptResponse out = creativePromptSuggestService.suggest(body);
        if ("ok".equals(out.getStatus())) {
            return ResponseEntity.ok(out);
        }
        if (out.getMessage() != null && out.getMessage().contains("生成失败")) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(out);
        }
        return ResponseEntity.badRequest().body(out);
    }

    /**
     * 素材页一键：请求体仅含 userId / 计划 / 广告组等字段；服务端只序列化为 {@code task=material_image} 的 JSON 交给主对话模型，
     * 不拼接流程说明文案。
     */
    @PostMapping("/material-agent-run")
    public ResponseEntity<MaterialCreativeResponse> materialAgentRun(
            HttpServletRequest request,
            @RequestBody(required = false) MaterialCreativeRequest body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(MaterialCreativeResponse.error("请求体不能为空"));
        }
        MaterialCreativeResponse out = materialCreativeAgentService.run(body, request);
        if ("error".equals(out.getStatus())) {
            return ResponseEntity.badRequest().body(out);
        }
        if ("partial".equals(out.getStatus())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(out);
        }
        return ResponseEntity.ok(out);
    }
}
