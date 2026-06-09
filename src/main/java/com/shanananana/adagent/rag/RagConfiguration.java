package com.shanananana.adagent.rag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("!rag-mock")
@ConditionalOnProperty(prefix = "ad-agent.rag", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RagProperties.class)
public class RagConfiguration {

    @Bean
    public QdrantClient ragQdrantClient(Environment env) {
        String host = env.getProperty("spring.ai.vectorstore.qdrant.host", "localhost");
        int port = env.getProperty("spring.ai.vectorstore.qdrant.port", Integer.class, 6334);
        boolean useTls = env.getProperty("spring.ai.vectorstore.qdrant.use-tls", Boolean.class, false);
        String apiKey = env.getProperty("spring.ai.vectorstore.qdrant.api-key");
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, port, useTls);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.withApiKey(apiKey);
        }
        return new QdrantClient(builder.build());
    }

    @Bean(name = "ragVectorStore")
    @Lazy
    public VectorStore ragVectorStore(QdrantClient ragQdrantClient, EmbeddingModel embeddingModel, Environment env) {
        String collection = env.getProperty("spring.ai.vectorstore.qdrant.collection-name", "ad_agent_rag");
        boolean initSchema = env.getProperty("spring.ai.vectorstore.qdrant.initialize-schema", Boolean.class, true);
        return QdrantVectorStore.builder(ragQdrantClient, embeddingModel)
                .collectionName(collection)
                .initializeSchema(initSchema)
                .build();
    }
}
