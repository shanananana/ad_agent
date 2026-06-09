package com.shanananana.adagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * 本地验证用：内存向量库 + 确定性伪 embedding，不依赖 Qdrant / 外网 Embedding API。
 * 激活：{@code spring.profiles.active=rag,rag-mock}
 */
@Configuration
@Profile("rag-mock")
@ConditionalOnProperty(prefix = "ad-agent.rag", name = "enabled", havingValue = "true")
public class RagMockConfiguration {

    @Bean(name = "ragVectorStore")
    public VectorStore ragVectorStore() {
        return SimpleVectorStore.builder(new DeterministicEmbeddingModel()).build();
    }

    static final class DeterministicEmbeddingModel implements EmbeddingModel {
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
