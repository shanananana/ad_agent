package com.example.adagent.data;

import com.example.adagent.config.DataPathConfig;
import com.example.adagent.data.dto.ChatSessionRecord;
import com.example.adagent.data.dto.UserSessionsIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <strong>聊天记录</strong>持久化：会话文件 {@code data/chat/sessions/{sessionId}.json} + 用户索引
 * {@code data/chat/users/{userId}/sessions.json}；支持追加消息、列举会话、删除单会话及按用户批量清除（含目录扫描防漏删）。
 */
@Repository
public class ChatHistoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryRepository.class);
    private static final int MAX_MESSAGES_PER_SESSION = 100;

    private final DataPathConfig dataPathConfig;
    private final ObjectMapper objectMapper;
    private final java.util.Map<String, ReentrantLock> sessionLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public ChatHistoryRepository(DataPathConfig dataPathConfig, ObjectMapper objectMapper) {
        this.dataPathConfig = dataPathConfig;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void ensureDirs() throws IOException {
        Files.createDirectories(dataPathConfig.getBaseDir().resolve("chat").resolve("sessions"));
        Files.createDirectories(dataPathConfig.getBaseDir().resolve("chat").resolve("users"));
        logger.info("【聊天记录】目录已就绪");
    }

    private ReentrantLock lockFor(String sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
    }

    /** 创建新会话并写入用户索引 */
    public void saveNewSession(String sessionId, String userId) {
        if (sessionId == null || sessionId.isBlank() || userId == null || userId.isBlank()) {
            return;
        }
        ReentrantLock lock = lockFor(sessionId);
        lock.lock();
        try {
            String now = Instant.now().toString();
            ChatSessionRecord record = new ChatSessionRecord();
            record.setSessionId(sessionId);
            record.setUserId(userId);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setMessages(new ArrayList<>());
            var path = dataPathConfig.getChatSessionPath(sessionId);
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), record);

            UserSessionsIndex index = loadUserSessions(userId);
            index.setUserId(userId);
            index.setUpdatedAt(now);
            UserSessionsIndex.SessionMeta meta = new UserSessionsIndex.SessionMeta();
            meta.setSessionId(sessionId);
            meta.setCreatedAt(now);
            meta.setUpdatedAt(now);
            index.getSessions().add(0, meta);
            saveUserSessions(userId, index);
            logger.info("【聊天记录】新建会话 {} 用户 {}", sessionId, userId);
        } catch (Exception e) {
            logger.error("【聊天记录】saveNewSession 失败", e);
            throw new RuntimeException("保存会话失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 追加一条消息 */
    public void appendMessage(String sessionId, String role, String content) {
        appendMessage(sessionId, role, content, null);
    }

    /**
     * 追加一条消息；{@code thinking} 仅对助手消息有意义，写入会话 JSON 供历史恢复展示。
     */
    public void appendMessage(String sessionId, String role, String content, String thinking) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ReentrantLock lock = lockFor(sessionId);
        lock.lock();
        try {
            ChatSessionRecord record = loadSession(sessionId);
            if (record == null) {
                return;
            }
            String t = (thinking != null && !thinking.isBlank() && "assistant".equals(role)) ? thinking.trim() : null;
            record.getMessages().add(new ChatSessionRecord.MessageEntry(role, content, t));
            if (record.getMessages().size() > MAX_MESSAGES_PER_SESSION) {
                record.setMessages(new ArrayList<>(record.getMessages().subList(record.getMessages().size() - MAX_MESSAGES_PER_SESSION, record.getMessages().size())));
            }
            String now = Instant.now().toString();
            record.setUpdatedAt(now);
            objectMapper.writeValue(dataPathConfig.getChatSessionPath(sessionId).toFile(), record);

            String userId = record.getUserId();
            if (userId != null && !userId.isBlank()) {
                UserSessionsIndex index = loadUserSessions(userId);
                index.setUpdatedAt(now);
                for (UserSessionsIndex.SessionMeta m : index.getSessions()) {
                    if (sessionId.equals(m.getSessionId())) {
                        m.setUpdatedAt(now);
                        break;
                    }
                }
                saveUserSessions(userId, index);
            }
        } catch (Exception e) {
            logger.warn("【聊天记录】appendMessage 失败: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** 按 sessionId 加载会话（含消息） */
    public ChatSessionRecord loadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        var path = dataPathConfig.getChatSessionPath(sessionId);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return objectMapper.readValue(path.toFile(), ChatSessionRecord.class);
        } catch (Exception e) {
            logger.warn("【聊天记录】loadSession 失败 {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /** 某用户的会话列表（按 updatedAt 倒序） */
    public List<UserSessionsIndex.SessionMeta> listSessionsByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        UserSessionsIndex index = loadUserSessions(userId);
        return index.getSessions().stream()
                .sorted((a, b) -> (b.getUpdatedAt() != null && a.getUpdatedAt() != null)
                        ? b.getUpdatedAt().compareTo(a.getUpdatedAt()) : 0)
                .collect(Collectors.toList());
    }

    /**
     * 合并用户索引中的会话与磁盘 sessions 目录下「文件内 userId 一致」的会话，用于清除聊天时不漏删孤儿文件。
     */
    public LinkedHashSet<String> collectSessionIdsForUser(String userId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (userId == null || userId.isBlank()) {
            return ids;
        }
        String uid = userId.trim();
        for (UserSessionsIndex.SessionMeta m : listSessionsByUser(uid)) {
            if (m.getSessionId() != null && !m.getSessionId().isBlank()) {
                ids.add(m.getSessionId().trim());
            }
        }
        Path sessionsDir = dataPathConfig.getBaseDir().resolve("chat").resolve("sessions");
        if (!Files.isDirectory(sessionsDir)) {
            return ids;
        }
        try (Stream<Path> stream = Files.list(sessionsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    ChatSessionRecord r = objectMapper.readValue(p.toFile(), ChatSessionRecord.class);
                    if (r == null || r.getUserId() == null || r.getSessionId() == null) {
                        return;
                    }
                    if (uid.equals(r.getUserId().trim())) {
                        ids.add(r.getSessionId().trim());
                    }
                } catch (Exception e) {
                    logger.debug("【聊天记录】扫描会话目录跳过 {}: {}", p.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            logger.warn("【聊天记录】列举 sessions 目录失败: {}", e.getMessage());
        }
        return ids;
    }

    /** 删除会话：删除会话文件并从用户索引中移除 */
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ReentrantLock lock = lockFor(sessionId);
        lock.lock();
        try {
            ChatSessionRecord record = loadSession(sessionId);
            String userId = record != null ? record.getUserId() : null;
            var path = dataPathConfig.getChatSessionPath(sessionId);
            if (Files.exists(path)) {
                Files.delete(path);
                logger.info("【聊天记录】删除会话文件 {}", sessionId);
            }
            if (userId != null && !userId.isBlank()) {
                UserSessionsIndex index = loadUserSessions(userId);
                index.getSessions().removeIf(m -> sessionId.equals(m.getSessionId()));
                index.setUpdatedAt(Instant.now().toString());
                saveUserSessions(userId, index);
            }
        } catch (IOException e) {
            logger.warn("【聊天记录】deleteSession 失败 {}: {}", sessionId, e.getMessage());
            throw new RuntimeException("删除会话失败", e);
        } catch (Exception e) {
            logger.error("【聊天记录】deleteSession 失败", e);
            throw new RuntimeException("删除会话失败", e);
        } finally {
            lock.unlock();
        }
    }

    private UserSessionsIndex loadUserSessions(String userId) {
        var path = dataPathConfig.getChatUserSessionsPath(userId);
        if (!Files.exists(path)) {
            UserSessionsIndex idx = new UserSessionsIndex();
            idx.setUserId(userId);
            idx.setSessions(new ArrayList<>());
            return idx;
        }
        try {
            UserSessionsIndex idx = objectMapper.readValue(path.toFile(), UserSessionsIndex.class);
            if (idx.getSessions() == null) idx.setSessions(new ArrayList<>());
            return idx;
        } catch (Exception e) {
            UserSessionsIndex idx = new UserSessionsIndex();
            idx.setUserId(userId);
            idx.setSessions(new ArrayList<>());
            return idx;
        }
    }

    private void saveUserSessions(String userId, UserSessionsIndex index) throws IOException {
        var path = dataPathConfig.getChatUserSessionsPath(userId);
        Files.createDirectories(path.getParent());
        objectMapper.writeValue(path.toFile(), index);
    }
}
