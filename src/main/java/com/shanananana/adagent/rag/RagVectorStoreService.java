package com.shanananana.adagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RagVectorStoreService {

    private static final Logger logger = LoggerFactory.getLogger(RagVectorStoreService.class);

    private final RagProperties ragProperties;
    private final Optional<VectorStore> ragVectorStore;

    public RagVectorStoreService(
            RagProperties ragProperties,
            @Autowired(required = false) @Qualifier("ragVectorStore") VectorStore ragVectorStore) {
        this.ragProperties = ragProperties;
        this.ragVectorStore = Optional.ofNullable(ragVectorStore);
    }

    public boolean isActive() {
        return ragProperties.isEnabled() && ragVectorStore.isPresent();
    }

    public void addAll(List<Document> documents) {
        if (!isActive() || documents == null || documents.isEmpty()) {
            return;
        }
        ragVectorStore.get().add(documents);
    }

    public List<Document> search(String query, String userId, String type) {
        if (!isActive() || query == null || query.isBlank() || userId == null || userId.isBlank()) {
            return List.of();
        }
        String safeUser = escapeFilterValue(userId.trim());
        String safeType = escapeFilterValue(type);
        SearchRequest request = SearchRequest.builder()
                .query(query.trim())
                .topK(ragProperties.getTopK())
                .similarityThreshold(ragProperties.getSimilarityThreshold())
                .filterExpression(
                        RagTypes.META_USER_ID + " == '" + safeUser + "' && "
                                + RagTypes.META_TYPE + " == '" + safeType + "'")
                .build();
        return ragVectorStore.get().similaritySearch(request);
    }

    public void deleteByUserAndType(String userId, String type) {
        if (!isActive() || userId == null || userId.isBlank()) {
            return;
        }
        String safeUser = escapeFilterValue(userId.trim());
        String safeType = escapeFilterValue(type);
        String filter = RagTypes.META_USER_ID + " == '" + safeUser + "' && "
                + RagTypes.META_TYPE + " == '" + safeType + "'";
        try {
            ragVectorStore.get().delete(filter);
            return;
        } catch (Exception e) {
            logger.debug("【RAG】metadata 过滤删除不可用，回退为按 id 删除: {}", e.getMessage());
        }
        deleteByIds(collectDocumentIds(userId.trim(), type));
    }

    public void deleteByIds(List<String> ids) {
        if (!isActive() || ids == null || ids.isEmpty()) {
            return;
        }
        ragVectorStore.get().delete(ids);
    }

    private List<String> collectDocumentIds(String userId, String type) {
        Set<String> ids = new LinkedHashSet<>();
        RagProperties wide = new RagProperties();
        wide.setTopK(50);
        wide.setSimilarityThreshold(0.0);
        for (String probe : List.of("用户", "内容", "记忆", "投放", "偏好", "广告")) {
            List<Document> hits = searchInternal(probe, userId, type, wide);
            for (Document doc : hits) {
                if (doc.getId() != null) {
                    ids.add(doc.getId());
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private List<Document> searchInternal(String query, String userId, String type, RagProperties props) {
        String safeUser = escapeFilterValue(userId);
        String safeType = escapeFilterValue(type);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(props.getTopK())
                .similarityThreshold(props.getSimilarityThreshold())
                .filterExpression(
                        RagTypes.META_USER_ID + " == '" + safeUser + "' && "
                                + RagTypes.META_TYPE + " == '" + safeType + "'")
                .build();
        return ragVectorStore.get().similaritySearch(request);
    }

    private static String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }
}
