package com.example.adagent.data;

import com.example.adagent.config.DataPathConfig;
import com.example.adagent.data.dto.ContentCatalogFile;
import com.example.adagent.data.dto.ContentItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * 按用户隔离的全局内容目录：{@code data/base/users/{userId}/contents.json}。
 */
@Repository
public class ContentCatalogRepository {

    private static final Logger logger = LoggerFactory.getLogger(ContentCatalogRepository.class);

    private final DataPathConfig dataPathConfig;
    private final ObjectMapper objectMapper;

    public ContentCatalogRepository(DataPathConfig dataPathConfig, ObjectMapper objectMapper) {
        this.dataPathConfig = dataPathConfig;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ContentCatalogFile load(String userId) {
        java.nio.file.Path path = dataPathConfig.getContentsDataPath(userId);
        if (!Files.exists(path)) {
            if (userId != null && !userId.isBlank()) {
                ensureUserContentsFromTemplate(userId);
                path = dataPathConfig.getContentsDataPath(userId);
            }
            if (!Files.exists(path)) {
                return emptyFile();
            }
        }
        try {
            String json = Files.readString(path);
            ContentCatalogFile file = objectMapper.readValue(json, ContentCatalogFile.class);
            if (file.getContents() == null) {
                file.setContents(new ArrayList<>());
            }
            return file;
        } catch (Exception e) {
            logger.error("【数据层】读取全局内容目录失败: {}", path, e);
            return emptyFile();
        }
    }

    private void ensureUserContentsFromTemplate(String userId) {
        java.nio.file.Path userPath = dataPathConfig.getContentsDataPath(userId);
        java.nio.file.Path templatePath = dataPathConfig.getContentsTemplatePath();
        try {
            Files.createDirectories(userPath.getParent());
            if (Files.exists(templatePath)) {
                Files.copy(templatePath, userPath);
                logger.info("【数据层】已从模板为 userId={} 初始化 contents.json", userId);
            } else {
                save(userId, emptyFile());
                logger.info("【数据层】无内容模板，已为 userId={} 写入空 contents.json", userId);
            }
        } catch (IOException e) {
            logger.error("【数据层】初始化用户 contents.json 失败 userId={}", userId, e);
            throw new RuntimeException("初始化用户 contents.json 失败", e);
        }
    }

    public void save(String userId, ContentCatalogFile file) {
        file.setUpdatedAt(Instant.now().toString());
        java.nio.file.Path path = dataPathConfig.getContentsDataPath(userId);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), file);
            logger.info("【数据层】已写入全局内容目录: {}", path);
        } catch (Exception e) {
            logger.error("【数据层】写入全局内容目录失败: {}", path, e);
            throw new RuntimeException("写入全局内容目录失败", e);
        }
    }

    private static ContentCatalogFile emptyFile() {
        ContentCatalogFile f = new ContentCatalogFile();
        f.setUpdatedAt(Instant.now().toString());
        f.setContents(new ArrayList<>());
        return f;
    }

    public Optional<ContentItem> findById(String userId, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return load(userId).getContents().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst();
    }

    public void upsert(String userId, ContentItem item) {
        ContentCatalogFile file = load(userId);
        var list = new ArrayList<>(file.getContents());
        list.removeIf(c -> item.getId() != null && item.getId().equals(c.getId()));
        list.add(item);
        file.setContents(list);
        save(userId, file);
    }

    public static String newContentId() {
        return UUID.randomUUID().toString();
    }
}
