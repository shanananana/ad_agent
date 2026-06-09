package com.shanananana.adagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

final class RagDocumentFactory {

    private RagDocumentFactory() {}

    static Document contentDocument(String userId, String contentId, String name, String summary) {
        String text = (StringUtils.hasText(name) ? name.trim() : "")
                + "\n"
                + (StringUtils.hasText(summary) ? summary.trim() : "");
        Map<String, Object> meta = baseMeta(userId, RagTypes.TYPE_CONTENT, contentId);
        if (StringUtils.hasText(name)) {
            meta.put(RagTypes.META_NAME, name.trim());
        }
        return new Document(contentId, text.trim(), meta);
    }

    static Document memoryDocument(String userId, String sourceId, String summary, String createdAt) {
        Map<String, Object> meta = baseMeta(userId, RagTypes.TYPE_MEMORY, sourceId);
        if (StringUtils.hasText(createdAt)) {
            meta.put(RagTypes.META_CREATED_AT, createdAt);
        }
        return new Document(sourceId, summary != null ? summary.trim() : "", meta);
    }

    private static Map<String, Object> baseMeta(String userId, String type, String sourceId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(RagTypes.META_USER_ID, userId);
        meta.put(RagTypes.META_TYPE, type);
        meta.put(RagTypes.META_SOURCE_ID, sourceId);
        return meta;
    }
}
