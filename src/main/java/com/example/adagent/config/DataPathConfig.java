package com.example.adagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地数据目录配置，运行时从单独目录读写，便于查找与持久化。
 */
@Configuration
@ConfigurationProperties(prefix = "ad-agent.data")
public class DataPathConfig {

    /**
     * 数据根目录，默认为运行目录下的 data/
     */
    private String basePath = "./data";

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public Path getBaseDir() {
        return Paths.get(basePath).toAbsolutePath().normalize();
    }

    /** 只读模板：最基础数据，应用禁止修改此文件。新用户无数据时从此复制。 */
    public Path getBaseDataTemplatePath() {
        return getBaseDir().resolve("base").resolve("_template_campaigns.json");
    }

    /** 全局基础数据（userId 为空时使用，兼容旧逻辑） */
    public Path getBaseDataPath() {
        return getBaseDir().resolve("base").resolve("campaigns.json");
    }

    /** 按用户隔离的基础数据；userId 为 null/空时返回全局路径 */
    public Path getBaseDataPath(String userId) {
        if (userId == null || userId.isBlank()) {
            return getBaseDataPath();
        }
        return getBaseDir().resolve("base").resolve("users").resolve(sanitizeId(userId)).resolve("campaigns.json");
    }

    /** 全局效果数据（未按用户隔离时使用） */
    public Path getPerformanceDataPath() {
        return getBaseDir().resolve("performance").resolve("performance.json");
    }

    /** 按用户隔离的效果数据文件；userId 为 null/空时返回全局路径 */
    public Path getPerformanceDataPath(String userId) {
        if (userId == null || userId.isBlank()) {
            return getPerformanceDataPath();
        }
        return getBaseDir().resolve("performance").resolve("users").resolve(sanitizeId(userId)).resolve("performance.json");
    }

    /** 长期记忆目录（按用户分文件） */
    public Path getLongTermMemoryDir() {
        return getBaseDir().resolve("long_term_memory");
    }

    /** 指定用户的长期记忆文件，userId 仅保留字母数字下划线 */
    public Path getLongTermMemoryPath(String userId) {
        if (userId == null || userId.isBlank()) {
            userId = "default";
        }
        String safe = userId.replaceAll("[^a-zA-Z0-9_]", "_");
        return getLongTermMemoryDir().resolve(safe + ".json");
    }

    /** 聊天记录：单会话文件 */
    public Path getChatSessionPath(String sessionId) {
        return getBaseDir().resolve("chat").resolve("sessions").resolve(sanitizeId(sessionId) + ".json");
    }

    /** 聊天记录：某用户的会话列表索引 */
    public Path getChatUserSessionsPath(String userId) {
        return getBaseDir().resolve("chat").resolve("users").resolve(sanitizeId(userId)).resolve("sessions.json");
    }

    /** 供 getPerformanceDataPath(userId) 等使用 */
    public static String sanitizeId(String id) {
        if (id == null || id.isBlank()) return "default";
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
