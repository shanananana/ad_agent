package com.shanananana.adagent.rag;

import com.shanananana.adagent.data.ContentCatalogRepository;
import com.shanananana.adagent.data.dto.ContentItem;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentRagRetriever {

    private final ContentCatalogRepository contentCatalogRepository;
    private final RagVectorStoreService ragVectorStoreService;

    public ContentRagRetriever(
            ContentCatalogRepository contentCatalogRepository,
            RagVectorStoreService ragVectorStoreService) {
        this.contentCatalogRepository = contentCatalogRepository;
        this.ragVectorStoreService = ragVectorStoreService;
    }

    /**
     * 格式化为 Prompt 中的「内容库」区块。
     */
    public String formatForPrompt(String userId, String query, String contentId) {
        if (userId == null || userId.isBlank()) {
            return "（未指定用户。）\n";
        }
        StringBuilder block = new StringBuilder();
        if (StringUtils.hasText(contentId)) {
            ContentItem exact = contentCatalogRepository.findById(userId, contentId.trim()).orElse(null);
            if (exact != null) {
                block.append("【已选内容库条目】\n");
                block.append("名称: ").append(nullToEmpty(exact.getName())).append("\n");
                block.append("摘要: ").append(nullToEmpty(exact.getSummary())).append("\n\n");
            } else {
                block.append("（未找到 contentId=").append(contentId).append(" 的内容条目）\n\n");
            }
        }
        if (!ragVectorStoreService.isActive()) {
            if (block.isEmpty()) {
                block.append("（未指定内容库条目。）\n");
            }
            return block.toString();
        }
        if (!StringUtils.hasText(query)) {
            if (block.isEmpty()) {
                block.append("（未指定内容库检索 query。）\n");
            }
            return block.toString();
        }
        List<Document> hits = ragVectorStoreService.search(query.trim(), userId.trim(), RagTypes.TYPE_CONTENT);
        if (hits.isEmpty()) {
            if (block.isEmpty()) {
                block.append("（语义检索未命中相关内容库条目。）\n");
            }
            return block.toString();
        }
        block.append("【语义检索相关内容库（top ").append(hits.size()).append("）】\n");
        int i = 1;
        for (Document doc : hits) {
            block.append(i++).append(". ");
            Object name = doc.getMetadata().get(RagTypes.META_NAME);
            if (name != null && StringUtils.hasText(name.toString())) {
                block.append(name).append("\n   ");
            }
            block.append(doc.getText().replace("\n", " ")).append("\n");
        }
        block.append("\n");
        return block.toString();
    }

    public List<Document> search(String userId, String query) {
        if (!ragVectorStoreService.isActive()) {
            return List.of();
        }
        return ragVectorStoreService.search(query, userId, RagTypes.TYPE_CONTENT);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
