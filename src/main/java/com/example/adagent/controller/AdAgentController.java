package com.example.adagent.controller;

import com.example.adagent.config.DataPathConfig;
import com.example.adagent.controller.StreamEvent;
import com.example.adagent.service.AdChatSessionService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ad-agent")
public class AdAgentController {

    private final AdChatSessionService chatSessionService;
    private final DataPathConfig dataPathConfig;

    public AdAgentController(AdChatSessionService chatSessionService, DataPathConfig dataPathConfig) {
        this.chatSessionService = chatSessionService;
        this.dataPathConfig = dataPathConfig;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "消息不能为空");
            return err;
        }
        String userId = request.get("userId");
        String sessionId = request.get("sessionId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatSessionService.createSession(userId != null ? userId.trim() : null);
            if (userId != null && !userId.trim().isEmpty()) {
                chatSessionService.bindUserToSession(sessionId, userId.trim());
            }
        } else if (userId != null && !userId.trim().isEmpty()) {
            chatSessionService.bindUserToSession(sessionId, userId.trim());
        }
        var turn = chatSessionService.chat(sessionId, userMessage);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("response", turn.response());
        result.put("suggestPageRefresh", turn.suggestPageRefresh());
        return result;
    }

    @PostMapping("/chat-with-history")
    public Map<String, Object> chatWithHistory(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String sessionId = request.get("sessionId");
        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "消息不能为空");
            return err;
        }
        String userId = request.get("userId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatSessionService.createSession(userId != null ? userId.trim() : null);
            if (userId != null && !userId.trim().isEmpty()) {
                chatSessionService.bindUserToSession(sessionId, userId.trim());
            }
        } else if (userId != null && !userId.trim().isEmpty()) {
            chatSessionService.bindUserToSession(sessionId, userId.trim());
        }
        var turn = chatSessionService.chat(sessionId, userMessage);
        List<Map<String, String>> history = chatSessionService.getHistory(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("response", turn.response());
        result.put("suggestPageRefresh", turn.suggestPageRefresh());
        result.put("history", history);
        return result;
    }

    @PostMapping("/session/create")
    public Map<String, String> createSession(@RequestBody(required = false) Map<String, String> request) {
        String userId = request != null ? request.get("userId") : null;
        String sessionId = chatSessionService.createSession(userId != null && !userId.trim().isEmpty() ? userId.trim() : null);
        if (userId != null && !userId.trim().isEmpty()) {
            chatSessionService.bindUserToSession(sessionId, userId.trim());
        }
        Map<String, String> result = new HashMap<>();
        result.put("sessionId", sessionId);
        return result;
    }

    @GetMapping("/sessions")
    public Map<String, Object> listSessions(@RequestParam("userId") String userId) {
        List<com.example.adagent.data.dto.UserSessionsIndex.SessionMeta> sessions = chatSessionService.listSessionsByUser(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("sessions", sessions);
        return result;
    }

    @GetMapping("/session/history")
    public Map<String, Object> getHistory(@RequestParam("sessionId") String sessionId) {
        List<Map<String, String>> history = chatSessionService.getHistory(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("history", history);
        return result;
    }

    @PostMapping("/session/clear")
    public Map<String, String> clearSession(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        chatSessionService.clearSession(sessionId);
        Map<String, String> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "会话已清除");
        return result;
    }

    @DeleteMapping("/session")
    public Map<String, Object> deleteSession(@RequestParam("sessionId") String sessionId) {
        Map<String, Object> result = new HashMap<>();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "sessionId 不能为空");
            return result;
        }
        chatSessionService.deleteSession(sessionId.trim());
        result.put("status", "success");
        result.put("message", "会话已删除");
        return result;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatSessionService.createSession(null);
        }
        final String sid = sessionId;
        return chatSessionService.streamChat(sid, message)
                .map(chunk -> ServerSentEvent.builder(chunk).build())
                .onErrorResume(e -> Flux.just(ServerSentEvent.builder("[错误: " + e.getMessage() + "]").build()));
    }

    /** 流式对话并推送思考过程（SSE event: thinking / content；隐私清除成功时额外 event: client，data: {"reload":true}） */
    @PostMapping(value = "/stream-with-thinking", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamWithThinking(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        if (message == null || message.trim().isEmpty()) {
            return Flux.just(ServerSentEvent.builder("[错误: 消息不能为空]").build());
        }
        String userId = request.get("userId");
        String sessionId = request.get("sessionId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatSessionService.createSession(userId != null && !userId.trim().isEmpty() ? userId.trim() : null);
            if (userId != null && !userId.trim().isEmpty()) {
                chatSessionService.bindUserToSession(sessionId, userId.trim());
            }
        } else if (userId != null && !userId.trim().isEmpty()) {
            chatSessionService.bindUserToSession(sessionId, userId.trim());
        }
        final String sid = sessionId;
        return chatSessionService.streamChatWithThinking(sid, message)
                .map(ev -> ServerSentEvent.builder(ev.data()).event(ev.type()).build())
                .onErrorResume(e -> Flux.just(
                        ServerSentEvent.builder("[错误: " + e.getMessage() + "]").event("content").build()));
    }

    /** 运行时数据目录（便于确认能查到） */
    @GetMapping("/data-path")
    public Map<String, String> dataPath() {
        Map<String, String> result = new HashMap<>();
        result.put("basePath", dataPathConfig.getBasePath());
        result.put("baseDataFile", dataPathConfig.getBaseDataPath().toString());
        result.put("performanceDataFile", dataPathConfig.getPerformanceDataPath().toString());
        result.put("longTermMemoryDir", dataPathConfig.getLongTermMemoryDir().toString());
        return result;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        result.put("service", "AD Agent");
        return result;
    }
}
