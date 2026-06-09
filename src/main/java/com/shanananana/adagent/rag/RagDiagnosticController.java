package com.shanananana.adagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ad-agent/rag/diagnostics")
@ConditionalOnProperty(prefix = "ad-agent.rag", name = "diagnostics", havingValue = "true")
public class RagDiagnosticController {

    private final RagVectorStoreService ragVectorStoreService;
    private final ContentRagRetriever contentRagRetriever;
    private final ContentRagIndexService contentRagIndexService;
    private final MemoryRagIndexService memoryRagIndexService;

    public RagDiagnosticController(
            RagVectorStoreService ragVectorStoreService,
            ContentRagRetriever contentRagRetriever,
            ContentRagIndexService contentRagIndexService,
            MemoryRagIndexService memoryRagIndexService) {
        this.ragVectorStoreService = ragVectorStoreService;
        this.contentRagRetriever = contentRagRetriever;
        this.contentRagIndexService = contentRagIndexService;
        this.memoryRagIndexService = memoryRagIndexService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "ragActive", ragVectorStoreService.isActive(),
                "message", ragVectorStoreService.isActive() ? "RAG 已启用" : "RAG 未激活");
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String userId,
            @RequestParam String query,
            @RequestParam(defaultValue = "content") String type) {
        if (!ragVectorStoreService.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "RAG 未激活"));
        }
        List<Document> docs = ragVectorStoreService.search(query, userId, type);
        List<Map<String, Object>> hits = docs.stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "text", d.getText(),
                        "metadata", d.getMetadata()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("count", hits.size(), "hits", hits));
    }

    @GetMapping("/reindex")
    public Map<String, String> reindex(@RequestParam String userId) {
        contentRagIndexService.reindexUser(userId);
        memoryRagIndexService.reindexUser(userId);
        return Map.of("status", "ok", "userId", userId);
    }

    @GetMapping("/content-prompt-block")
    public Map<String, String> contentPromptBlock(
            @RequestParam String userId,
            @RequestParam String query,
            @RequestParam(required = false) String contentId) {
        return Map.of("block", contentRagRetriever.formatForPrompt(userId, query, contentId));
    }
}
