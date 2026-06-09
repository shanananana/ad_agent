package com.shanananana.adagent.rag;

import com.shanananana.adagent.data.LongTermMemoryRepository;
import com.shanananana.adagent.data.dto.LongTermMemoryFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MemoryRagIndexService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryRagIndexService.class);

    private final LongTermMemoryRepository longTermMemoryRepository;
    private final RagVectorStoreService ragVectorStoreService;

    public MemoryRagIndexService(
            LongTermMemoryRepository longTermMemoryRepository,
            RagVectorStoreService ragVectorStoreService) {
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.ragVectorStoreService = ragVectorStoreService;
    }

    public void indexEntry(String userId, String summary, String createdAt) {
        if (!ragVectorStoreService.isActive() || userId == null || userId.isBlank()
                || summary == null || summary.isBlank()) {
            return;
        }
        String sourceId = UUID.randomUUID().toString();
        Document doc = RagDocumentFactory.memoryDocument(userId.trim(), sourceId, summary, createdAt);
        ragVectorStoreService.addAll(List.of(doc));
        logger.debug("【RAG-memory】已追加向量 userId={}", userId);
    }

    public void reindexUser(String userId) {
        if (!ragVectorStoreService.isActive() || userId == null || userId.isBlank()) {
            return;
        }
        ragVectorStoreService.deleteByUserAndType(userId.trim(), RagTypes.TYPE_MEMORY);
        LongTermMemoryFile file = longTermMemoryRepository.load(userId.trim());
        List<LongTermMemoryFile.MemoryEntry> memories = file.getMemories();
        if (memories == null || memories.isEmpty()) {
            return;
        }
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < memories.size(); i++) {
            LongTermMemoryFile.MemoryEntry entry = memories.get(i);
            if (entry == null || entry.getSummary() == null || entry.getSummary().isBlank()) {
                continue;
            }
            // Qdrant 点 ID 须为合法 UUID；业务来源 id 放 metadata.source_id
            String sourceId = UUID.randomUUID().toString();
            docs.add(RagDocumentFactory.memoryDocument(
                    userId.trim(), sourceId, entry.getSummary(), entry.getAt()));
        }
        ragVectorStoreService.addAll(docs);
        logger.info("【RAG-memory】已全量索引 userId={} 条数={}", userId, docs.size());
    }

    public void deleteVectorsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        ragVectorStoreService.deleteByUserAndType(userId.trim(), RagTypes.TYPE_MEMORY);
        logger.info("【RAG-memory】已删除向量 userId={}", userId);
    }
}
