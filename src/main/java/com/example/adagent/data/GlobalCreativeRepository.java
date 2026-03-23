package com.example.adagent.data;

import com.example.adagent.config.DataPathConfig;
import com.example.adagent.data.dto.GlobalCreative;
import com.example.adagent.data.dto.GlobalCreativeFile;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 按用户隔离的全局素材（创意）目录：{@code data/base/users/{userId}/creatives.json}。
 */
@Repository
public class GlobalCreativeRepository {

    private static final Logger logger = LoggerFactory.getLogger(GlobalCreativeRepository.class);

    private final DataPathConfig dataPathConfig;
    private final ObjectMapper objectMapper;

    public GlobalCreativeRepository(DataPathConfig dataPathConfig, ObjectMapper objectMapper) {
        this.dataPathConfig = dataPathConfig;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void ensureAssetDir() throws IOException {
        Files.createDirectories(dataPathConfig.getCreativeAssetsRoot());
    }

    public GlobalCreativeFile load(String userId) {
        java.nio.file.Path path = dataPathConfig.getCreativesDataPath(userId);
        if (!Files.exists(path)) {
            if (userId != null && !userId.isBlank()) {
                ensureUserCreativesFromTemplate(userId);
                path = dataPathConfig.getCreativesDataPath(userId);
            }
            if (!Files.exists(path)) {
                return emptyFile();
            }
        }
        try {
            String json = Files.readString(path);
            GlobalCreativeFile file = objectMapper.readValue(json, GlobalCreativeFile.class);
            if (file.getCreatives() == null) {
                file.setCreatives(new ArrayList<>());
            }
            return file;
        } catch (Exception e) {
            logger.error("【数据层】读取全局素材目录失败: {}", path, e);
            return emptyFile();
        }
    }

    private void ensureUserCreativesFromTemplate(String userId) {
        java.nio.file.Path userPath = dataPathConfig.getCreativesDataPath(userId);
        java.nio.file.Path templatePath = dataPathConfig.getCreativesTemplatePath();
        try {
            Files.createDirectories(userPath.getParent());
            if (Files.exists(templatePath)) {
                Files.copy(templatePath, userPath);
                logger.info("【数据层】已从模板为 userId={} 初始化 creatives.json", userId);
            } else {
                save(userId, emptyFile());
                logger.info("【数据层】无素材模板，已为 userId={} 写入空 creatives.json", userId);
            }
        } catch (IOException e) {
            logger.error("【数据层】初始化用户 creatives.json 失败 userId={}", userId, e);
            throw new RuntimeException("初始化用户 creatives.json 失败", e);
        }
    }

    public void save(String userId, GlobalCreativeFile file) {
        file.setUpdatedAt(Instant.now().toString());
        java.nio.file.Path path = dataPathConfig.getCreativesDataPath(userId);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writeValue(path.toFile(), file);
            logger.info("【数据层】已写入全局素材目录: {}", path);
        } catch (Exception e) {
            logger.error("【数据层】写入全局素材目录失败: {}", path, e);
            throw new RuntimeException("写入全局素材目录失败", e);
        }
    }

    private static GlobalCreativeFile emptyFile() {
        GlobalCreativeFile f = new GlobalCreativeFile();
        f.setUpdatedAt(Instant.now().toString());
        f.setCreatives(new ArrayList<>());
        return f;
    }

    public Optional<GlobalCreative> findById(String userId, String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return load(userId).getCreatives().stream()
                .filter(c -> id.equals(c.getId()))
                .findFirst();
    }

    public boolean exists(String userId, String id) {
        return findById(userId, id).isPresent();
    }

    public Map<String, GlobalCreative> findByIds(String userId, Iterable<String> ids) {
        Map<String, GlobalCreative> byId = load(userId).getCreatives().stream()
                .collect(Collectors.toMap(GlobalCreative::getId, c -> c, (a, b) -> a, LinkedHashMap::new));
        Map<String, GlobalCreative> out = new LinkedHashMap<>();
        for (String id : ids) {
            if (id != null && byId.containsKey(id)) {
                out.put(id, byId.get(id));
            }
        }
        return out;
    }

    public void upsert(String userId, GlobalCreative creative) {
        GlobalCreativeFile file = load(userId);
        List<GlobalCreative> list = new ArrayList<>(file.getCreatives());
        list.removeIf(c -> creative.getId() != null && creative.getId().equals(c.getId()));
        list.add(creative);
        file.setCreatives(list);
        save(userId, file);
    }

    public static String newCreativeId() {
        return UUID.randomUUID().toString();
    }
}
