package com.example.adagent.data;

import com.example.adagent.config.DataPathConfig;
import com.example.adagent.data.dto.LongTermMemoryFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * <strong>长期记忆</strong>持久化：每用户一个 {@code data/long_term_memory/{userId}.json}，提供加载、追加摘要与整文件删除（隐私清除）。
 */
@Repository
public class LongTermMemoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryRepository.class);
    private final DataPathConfig dataPathConfig;
    private final ObjectMapper objectMapper;
    private final java.util.Map<String, ReentrantLock> userLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public LongTermMemoryRepository(DataPathConfig dataPathConfig, ObjectMapper objectMapper) {
        this.dataPathConfig = dataPathConfig;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void ensureDir() throws IOException {
        Files.createDirectories(dataPathConfig.getLongTermMemoryDir());
        logger.info("【数据层-长期记忆】目录已就绪: {}", dataPathConfig.getLongTermMemoryDir());
    }

    private ReentrantLock lockFor(String userId) {
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    }

    public LongTermMemoryFile load(String userId) {
        if (userId == null || userId.isBlank()) {
            return emptyFile(userId);
        }
        var path = dataPathConfig.getLongTermMemoryPath(userId);
        if (!Files.exists(path)) {
            return emptyFile(userId);
        }
        try {
            String json = Files.readString(path);
            LongTermMemoryFile file = objectMapper.readValue(json, LongTermMemoryFile.class);
            if (file.getMemories() == null) {
                file.setMemories(new ArrayList<>());
            }
            return file;
        } catch (Exception e) {
            logger.warn("【数据层-长期记忆】读取失败 userId={}: {}", userId, e.getMessage());
            return emptyFile(userId);
        }
    }

    public void append(String userId, String summary) {
        if (userId == null || userId.isBlank() || summary == null || summary.isBlank()) {
            return;
        }
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            LongTermMemoryFile file = load(userId);
            file.setUserId(userId);
            file.getMemories().add(new LongTermMemoryFile.MemoryEntry(summary, Instant.now().toString()));
            file.setUpdatedAt(Instant.now().toString());
            var path = dataPathConfig.getLongTermMemoryPath(userId);
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), file);
            logger.info("【数据层-长期记忆】已追加 userId={}, 条数={}", userId, file.getMemories().size());
        } catch (Exception e) {
            logger.error("【数据层-长期记忆】写入失败 userId={}", userId, e);
            throw new RuntimeException("长期记忆写入失败", e);
        } finally {
            lock.unlock();
        }
    }

    /** 取最近 N 条记忆（倒序，最新的在前） */
    public List<LongTermMemoryFile.MemoryEntry> getRecent(String userId, int topK) {
        LongTermMemoryFile file = load(userId);
        List<LongTermMemoryFile.MemoryEntry> list = file.getMemories();
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        int from = Math.max(0, list.size() - topK);
        List<LongTermMemoryFile.MemoryEntry> sub = new ArrayList<>(list.subList(from, list.size()));
        Collections.reverse(sub);
        return sub;
    }

    private static LongTermMemoryFile emptyFile(String userId) {
        LongTermMemoryFile f = new LongTermMemoryFile();
        f.setUserId(userId);
        f.setMemories(new ArrayList<>());
        f.setUpdatedAt(Instant.now().toString());
        return f;
    }

    /**
     * 删除该用户的长期记忆文件（若存在）。与 {@link #append} 使用相同 userId 粒度锁。
     */
    public void deleteForUser(String userId) throws IOException {
        if (userId == null || userId.isBlank()) {
            return;
        }
        ReentrantLock lock = lockFor(userId);
        lock.lock();
        try {
            var path = dataPathConfig.getLongTermMemoryPath(userId);
            if (Files.exists(path)) {
                Files.delete(path);
                logger.info("【数据层-长期记忆】已删除文件 userId={} path={}", userId, path);
            }
        } finally {
            lock.unlock();
        }
    }
}
