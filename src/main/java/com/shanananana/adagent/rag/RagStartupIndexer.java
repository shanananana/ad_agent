package com.shanananana.adagent.rag;

import com.shanananana.adagent.config.DataPathConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class RagStartupIndexer {

    private static final Logger logger = LoggerFactory.getLogger(RagStartupIndexer.class);

    private final RagProperties ragProperties;
    private final RagVectorStoreService ragVectorStoreService;
    private final ContentRagIndexService contentRagIndexService;
    private final MemoryRagIndexService memoryRagIndexService;
    private final DataPathConfig dataPathConfig;

    public RagStartupIndexer(
            RagProperties ragProperties,
            RagVectorStoreService ragVectorStoreService,
            ContentRagIndexService contentRagIndexService,
            MemoryRagIndexService memoryRagIndexService,
            DataPathConfig dataPathConfig) {
        this.ragProperties = ragProperties;
        this.ragVectorStoreService = ragVectorStoreService;
        this.contentRagIndexService = contentRagIndexService;
        this.memoryRagIndexService = memoryRagIndexService;
        this.dataPathConfig = dataPathConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!ragProperties.isReindexOnStartup() || !ragVectorStoreService.isActive()) {
            return;
        }
        logger.info("【RAG】reindex-on-startup 已启用，开始扫描用户数据目录");
        reindexUsersFromBaseDir();
        reindexUsersFromMemoryDir();
    }

    private void reindexUsersFromBaseDir() {
        Path usersDir = dataPathConfig.getBaseDataPath().resolve("users");
        if (!Files.isDirectory(usersDir)) {
            return;
        }
        try (var stream = Files.list(usersDir)) {
            stream.filter(Files::isDirectory)
                    .forEach(dir -> {
                        String userId = dir.getFileName().toString();
                        contentRagIndexService.reindexUser(userId);
                    });
        } catch (Exception e) {
            logger.warn("【RAG】扫描 base/users 失败: {}", e.getMessage());
        }
    }

    private void reindexUsersFromMemoryDir() {
        Path memDir = dataPathConfig.getLongTermMemoryDir();
        if (!Files.isDirectory(memDir)) {
            return;
        }
        try (var stream = Files.list(memDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String userId = fileName.substring(0, fileName.length() - ".json".length());
                        memoryRagIndexService.reindexUser(userId);
                    });
        } catch (Exception e) {
            logger.warn("【RAG】扫描 long_term_memory 失败: {}", e.getMessage());
        }
    }
}
