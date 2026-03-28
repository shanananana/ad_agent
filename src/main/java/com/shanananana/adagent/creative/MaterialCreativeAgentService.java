package com.shanananana.adagent.creative;

import com.shanananana.adagent.agent.execution.ToolExecutionService;
import com.shanananana.adagent.creative.dto.MaterialCreativeRequest;
import com.shanananana.adagent.creative.dto.MaterialCreativeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 素材页一键：仅将结构化任务 JSON 交给主 {@link org.springframework.ai.chat.client.ChatClient}（含 Markdown skills），
 * 不拼接自然语言流程说明；工具链路由模型与 {@code chat-system} / 技能正文决定。
 */
@Service
public class MaterialCreativeAgentService {

    private static final Logger log = LoggerFactory.getLogger(MaterialCreativeAgentService.class);

    private static final String TASK_KEY = "material_image";

    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;

    public MaterialCreativeAgentService(ToolExecutionService toolExecutionService, ObjectMapper objectMapper) {
        this.toolExecutionService = toolExecutionService;
        this.objectMapper = objectMapper;
    }

    public MaterialCreativeResponse run(MaterialCreativeRequest req, HttpServletRequest httpRequest) {
        if (req == null) {
            return MaterialCreativeResponse.error("请求体不能为空");
        }
        if (!StringUtils.hasText(req.getUserId())) {
            return MaterialCreativeResponse.error("userId 不能为空");
        }
        if (!StringUtils.hasText(req.getCampaignId())) {
            return MaterialCreativeResponse.error("campaignId 不能为空");
        }
        if (!StringUtils.hasText(req.getAdGroupId())) {
            return MaterialCreativeResponse.error("adGroupId 不能为空");
        }

        String payload;
        try {
            payload = buildTaskPayload(req);
        } catch (Exception e) {
            log.error("【素材技能一键】构造任务 JSON 失败", e);
            return MaterialCreativeResponse.error("构造任务参数失败");
        }

        httpRequest.setAttribute(MaterialCreativeToolCapture.ATTR_CAPTURE, true);
        try {
            log.info("【素材技能一键】调用主 ChatClient（仅结构化 JSON，chars={}）", payload.length());
            String assistant = toolExecutionService.executeWithTools(payload, null);
            return assembleResponse(httpRequest, assistant);
        } catch (Exception e) {
            log.error("【素材技能一键】执行失败", e);
            return MaterialCreativeResponse.error(e.getMessage() != null ? e.getMessage() : "执行失败");
        } finally {
            httpRequest.removeAttribute(MaterialCreativeToolCapture.ATTR_CAPTURE);
            httpRequest.removeAttribute(MaterialCreativeToolCapture.ATTR_LAST_GENERATE_JSON);
        }
    }

    private String buildTaskPayload(MaterialCreativeRequest req) throws Exception {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("task", TASK_KEY);
        n.put("userId", req.getUserId().trim());
        n.put("campaignId", req.getCampaignId().trim());
        n.put("adGroupId", req.getAdGroupId().trim());
        if (StringUtils.hasText(req.getUserPrompt())) {
            n.put("userPrompt", req.getUserPrompt().trim());
        } else {
            n.putNull("userPrompt");
        }
        n.put("persist", req.isPersist());
        if (StringUtils.hasText(req.getTitle())) {
            n.put("title", req.getTitle().trim());
        }
        if (StringUtils.hasText(req.getContentId())) {
            n.put("contentId", req.getContentId().trim());
        }
        if (StringUtils.hasText(req.getPlacement())) {
            n.put("placement", req.getPlacement().trim());
        }
        n.put("bindToAdGroupAfterPersist", req.isBindToAdGroupAfterPersist());
        n.put("bindPrepend", req.isBindPrepend());
        return objectMapper.writeValueAsString(n);
    }

    private MaterialCreativeResponse assembleResponse(HttpServletRequest httpRequest, String assistantReply) {
        MaterialCreativeResponse out = new MaterialCreativeResponse();
        out.setAssistantReply(assistantReply);

        String genJson = (String) httpRequest.getAttribute(MaterialCreativeToolCapture.ATTR_LAST_GENERATE_JSON);

        if (StringUtils.hasText(genJson)) {
            try {
                JsonNode g = objectMapper.readTree(genJson);
                String st = textOrEmpty(g.get("status"));
                if ("ok".equals(st)) {
                    JsonNode u = g.get("imageUrl");
                    if (u != null && !u.isNull()) {
                        out.setImageUrl(u.asText());
                    }
                    JsonNode c = g.get("creativeId");
                    if (c != null && !c.isNull()) {
                        out.setCreativeId(c.asText());
                    }
                    JsonNode path = g.get("persistedPath");
                    if (path != null && !path.isNull()) {
                        out.setPersistedPath(path.asText());
                    }
                    out.setStatus("ok");
                } else {
                    out.setStatus("error");
                    JsonNode msg = g.get("message");
                    out.setMessage(msg != null && !msg.isNull() ? msg.asText() : "出图失败");
                }
            } catch (Exception e) {
                log.warn("【素材技能一键】解析出图工具 JSON 失败: {}", e.getMessage());
                out.setStatus("partial");
                out.setMessage("已收到模型回复，但未能解析出图工具返回");
            }
        } else {
            if (StringUtils.hasText(assistantReply)) {
                out.setStatus("partial");
                out.setMessage("未检测到出图工具调用，请查看模型说明或重试");
            } else {
                out.setStatus("error");
                out.setMessage("未完成有效输出");
            }
        }

        if ("ok".equals(out.getStatus()) && !StringUtils.hasText(out.getImageUrl()) && !StringUtils.hasText(out.getCreativeId())) {
            out.setStatus("partial");
            if (!StringUtils.hasText(out.getMessage())) {
                out.setMessage("工具返回中未包含 imageUrl/creativeId");
            }
        }

        return out;
    }

    private static String textOrEmpty(JsonNode n) {
        return n == null || n.isNull() ? "" : n.asText("");
    }
}
