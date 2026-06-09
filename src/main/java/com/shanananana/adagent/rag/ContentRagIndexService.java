package com.shanananana.adagent.rag;

import com.shanananana.adagent.data.ContentCatalogRepository;
import com.shanananana.adagent.data.dto.ContentItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentRagIndexService {

    private static final Logger logger = LoggerFactory.getLogger(ContentRagIndexService.class);

    private final ContentCatalogRepository contentCatalogRepository;
    private final RagVectorStoreService ragVectorStoreService;

    public ContentRagIndexService(
            ContentCatalogRepository contentCatalogRepository,
            RagVectorStoreService ragVectorStoreService) {
        this.contentCatalogRepository = contentCatalogRepository;
        this.ragVectorStoreService = ragVectorStoreService;
    }

    public void reindexUser(String userId) {
        if (!ragVectorStoreService.isActive() || userId == null || userId.isBlank()) {
            return;
        }
        ragVectorStoreService.deleteByUserAndType(userId.trim(), RagTypes.TYPE_CONTENT);
        List<ContentItem> items = contentCatalogRepository.load(userId.trim()).getContents();
        if (items == null || items.isEmpty()) {
            logger.info("【RAG-content】用户 {} 无内容库条目，跳过索引", userId);
            return;
        }
        List<Document> docs = new ArrayList<>();
        for (ContentItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            docs.add(RagDocumentFactory.contentDocument(
                    userId.trim(), item.getId(), item.getName(), item.getSummary()));
        }
        ragVectorStoreService.addAll(docs);
        logger.info("【RAG-content】已索引 userId={} 条数={}", userId, docs.size());
    }
}
