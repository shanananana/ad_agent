package com.shanananana.adagent.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVectorStoreServiceTest {

    private RagVectorStoreService service;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        props.setEnabled(true);
        props.setTopK(3);
        props.setSimilarityThreshold(0.3);
        EmbeddingModel embeddingModel = new HashEmbeddingModel();
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        service = new RagVectorStoreService(props, store);
        service.addAll(List.of(
                RagDocumentFactory.contentDocument("u1", "c1", "夏夜专辑", "青春流行专辑夏夜氛围主打歌"),
                RagDocumentFactory.contentDocument("u1", "c2", "首发冲刺", "首发周倒计时曲目列表"),
                RagDocumentFactory.memoryDocument("u1", "m1", "用户预算保守，不喜欢大幅加价", "2026-01-01")));
    }

    @Test
    void searchContentBySemanticQuery() {
        List<Document> hits = service.search("夏夜氛围开屏", "u1", RagTypes.TYPE_CONTENT);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getText().contains("夏夜"));
    }

    @Test
    void searchMemoryFiltersByUser() {
        List<Document> hits = service.search("预算", "u1", RagTypes.TYPE_MEMORY);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getText().contains("预算"));
    }

    @Test
    void searchDoesNotCrossUser() {
        List<Document> hits = service.search("预算", "other", RagTypes.TYPE_MEMORY);
        assertTrue(hits.isEmpty());
    }

  @Test
    void inactiveWhenDisabled() {
        RagProperties off = new RagProperties();
        off.setEnabled(false);
        RagVectorStoreService offService = new RagVectorStoreService(off, null);
        assertFalse(offService.isActive());
        assertTrue(offService.search("夏夜", "u1", RagTypes.TYPE_CONTENT).isEmpty());
    }

    /** 确定性伪 embedding，单元测试无需外网 API。 */
    static final class HashEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(embed(text), 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }

        @Override
        public float[] embed(String text) {
            float[] v = new float[16];
            int h = text != null ? text.hashCode() : 0;
            for (int i = 0; i < v.length; i++) {
                v[i] = ((h >> (i % 16)) & 0xff) / 255f;
            }
            return v;
        }
    }
}
