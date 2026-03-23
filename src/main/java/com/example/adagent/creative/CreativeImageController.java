package com.example.adagent.creative;

import com.example.adagent.creative.dto.GenerateImageRequest;
import com.example.adagent.creative.dto.GenerateImageResponse;
import com.example.adagent.creative.dto.SuggestPromptRequest;
import com.example.adagent.creative.dto.SuggestPromptResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创意文生图 API，与对话、自动调价隔离。
 */
@RestController
@RequestMapping("/api/ad-agent/creative")
public class CreativeImageController {

    private final CreativeImageService creativeImageService;
    private final CreativePromptSuggestService creativePromptSuggestService;

    public CreativeImageController(CreativeImageService creativeImageService,
                                   CreativePromptSuggestService creativePromptSuggestService) {
        this.creativeImageService = creativeImageService;
        this.creativePromptSuggestService = creativePromptSuggestService;
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
}
