package com.shanananana.adagent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时打印当前 {@link EmbeddingModel} 实现，便于确认本地 ONNX 是否生效。
 */
@Component
@ConditionalOnProperty(prefix = "ad-agent.rag", name = "enabled", havingValue = "true")
public class RagEmbeddingStartupLogger {

    private static final Logger logger = LoggerFactory.getLogger(RagEmbeddingStartupLogger.class);

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public RagEmbeddingStartupLogger(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logEmbeddingProvider() {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            logger.warn("【RAG Embedding】RAG 已启用但未发现 EmbeddingModel Bean");
            return;
        }
        String name = embeddingModel.getClass().getSimpleName();
        int dimensions = embeddingModel.dimensions();
        logger.info("【RAG Embedding】实现={} 向量维度={}", name, dimensions);
        if (name.contains("Transformers")) {
            logger.info("【RAG Embedding】使用本地 ONNX 句向量，无需外网 Embedding API");
        }
    }
}
