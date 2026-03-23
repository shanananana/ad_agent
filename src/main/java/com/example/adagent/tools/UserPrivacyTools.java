package com.example.adagent.tools;

import com.example.adagent.data.LongTermMemoryRepository;
import com.example.adagent.service.AdChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring AI <strong>隐私工具</strong>：声明清除长期记忆或聊天记录等工具供模型调用；实际删除以
 * {@link com.example.adagent.agent.AdAgentOrchestrator} 服务端同步删盘为准，避免仅「口头已删」。
 */
@Component
public class UserPrivacyTools {

    private static final Logger logger = LoggerFactory.getLogger(UserPrivacyTools.class);
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final AdChatSessionService adChatSessionService;

    public UserPrivacyTools(LongTermMemoryRepository longTermMemoryRepository,
                            @Lazy AdChatSessionService adChatSessionService) {
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.adChatSessionService = adChatSessionService;
    }

    @Tool(description = "清除当前用户的长期记忆文件（跨会话的偏好/习惯摘要）。当用户明确要求清除长期记忆、删掉偏好习惯、忘记历史偏好时使用。参数 userId 必须与对话上下文中【当前用户ID】一致；若未登录或无用户 ID 则不要调用，并告知用户无法执行。")
    public String clearUserLongTermMemory(String userId) {
        if (userId == null || userId.isBlank()) {
            return "未提供有效的用户 ID，无法清除长期记忆。请先登录或联系管理员。";
        }
        try {
            longTermMemoryRepository.deleteForUser(userId.trim());
            return "已清除该用户的长期记忆（偏好/习惯摘要文件已删除）。本轮起将不再注入历史偏好，直至产生新的记忆。";
        } catch (IOException e) {
            logger.error("clearUserLongTermMemory 失败 userId={}", userId, e);
            return "清除长期记忆失败：" + e.getMessage();
        }
    }

    @Tool(description = "删除当前用户在本地存储的全部聊天记录（所有会话文件及索引），并清理进程内对话缓存。当用户要求清空聊天记录、删除对话历史时使用。参数 userId 必须与【当前用户ID】一致；currentSessionId 传【当前会话ID】以便同时清理当前会话（可为空，但建议传入）。未登录时勿调用。")
    public String clearUserChatHistory(String userId, String currentSessionId) {
        if (userId == null || userId.isBlank()) {
            return "未提供有效的用户 ID，无法删除聊天记录。请先登录或联系管理员。";
        }
        try {
            String sid = (currentSessionId != null && !currentSessionId.isBlank()) ? currentSessionId.trim() : null;
            adChatSessionService.deleteAllChatHistoryForUser(userId.trim(), sid);
            return "已删除该用户的全部聊天记录（含索引中的会话与当前会话对应的持久化文件）。当前对话上下文已清空。";
        } catch (Exception e) {
            logger.error("clearUserChatHistory 失败 userId={}", userId, e);
            return "删除聊天记录失败：" + e.getMessage();
        }
    }
}
