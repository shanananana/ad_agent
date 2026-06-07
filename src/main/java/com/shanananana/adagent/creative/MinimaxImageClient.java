package com.shanananana.adagent.creative;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * MiniMax 原生文生图：{@code POST /v1/image_generation}（与 OpenAI {@code /v1/images/generations} 不同）。
 */
@Component
public class MinimaxImageClient {

    /** MiniMax API：prompt length must be less than 1500 */
    static final int PROMPT_MAX_LEN = 1499;

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public MinimaxImageClient(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${spring.ai.openai.image.options.model:image-01}") String model) {
        this.apiKey = apiKey;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = StringUtils.hasText(model) ? model : "image-01";
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl) && baseUrl.contains("minimaxi");
    }

    public GenerateResult generate(String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("MiniMax 文生图未配置 api-key 或 base-url");
        }
        String safePrompt = prompt != null && prompt.length() > PROMPT_MAX_LEN
                ? prompt.substring(0, PROMPT_MAX_LEN)
                : prompt;
        MinimaxImageRequest body = new MinimaxImageRequest(model, safePrompt, "1:1", "base64");
        MinimaxImageResponse response = restClient.post()
                .uri("/v1/image_generation")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(MinimaxImageResponse.class);
        if (response == null || response.baseResp() == null) {
            throw new IllegalStateException("MiniMax 文生图无响应");
        }
        if (response.baseResp().statusCode() != 0) {
            String msg = response.baseResp().statusMsg() != null ? response.baseResp().statusMsg() : "unknown";
            throw new IllegalStateException("MiniMax 文生图失败 status=" + response.baseResp().statusCode() + " " + msg);
        }
        if (response.data() == null || response.data().imageBase64() == null || response.data().imageBase64().isEmpty()) {
            throw new IllegalStateException("MiniMax 文生图未返回图片数据");
        }
        return new GenerateResult(null, response.data().imageBase64().get(0));
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    record GenerateResult(String url, String b64Json) {}

    record MinimaxImageRequest(
            String model,
            String prompt,
            @JsonProperty("aspect_ratio") String aspectRatio,
            @JsonProperty("response_format") String responseFormat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MinimaxImageResponse(
            @JsonProperty("base_resp") BaseResp baseResp,
            Data data) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record BaseResp(
                @JsonProperty("status_code") int statusCode,
                @JsonProperty("status_msg") String statusMsg) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Data(@JsonProperty("image_base64") List<String> imageBase64) {}
    }
}
