# 向量库与 RAG（Qdrant）

本项目选用 **Qdrant** 作为向量数据库，配合 Spring AI 实现 RAG（检索增强生成）。  
对话 LLM 配置见 [llm-provider-config.md](./llm-provider-config.md)。

## 1. 为什么选 Qdrant

| 考量 | 说明 |
|------|------|
| Spring AI 集成 | 官方 `spring-ai-starter-vector-store-qdrant`，与现有 Spring AI 1.0.3 栈一致 |
| 本地学习成本 | 一条 Docker 命令即可启动，自带 Dashboard |
| 元数据过滤 | 支持按 `user_id`、`type` 等 metadata 过滤，满足多用户隔离 |
| 规模 | 学习项目与中小规模生产均够用；超大规模再评估 Milvus 等 |

未选方案简述：`SimpleVectorStore` 不适合当真 RAG 底座；`pgvector` 需引入 PostgreSQL；Pinecone 为托管云服务，本地演示不便。

## 2. 本地启动 Qdrant

```bash
# 项目内脚本（推荐）
./scripts/qdrant/start-qdrant.sh

# 或手动
docker run -d --name ad-agent-qdrant \
  -p 6333:6333 -p 6334:6334 \
  -v ad_agent_qdrant_storage:/qdrant/storage \
  qdrant/qdrant:latest
```

| 端口 | 用途 |
|------|------|
| 6333 | HTTP API、Dashboard：<http://localhost:6333/dashboard> |
| 6334 | gRPC（Spring AI Qdrant starter 默认连此端口） |

停止与删除容器：

```bash
docker stop ad-agent-qdrant && docker rm ad-agent-qdrant
```

## 3. Maven 依赖（实现 RAG 时添加）

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-vector-store-qdrant</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-rag</artifactId>
</dependency>
```

版本由 `spring-ai-bom`（当前 1.0.3）统一管理，无需单独写 version。

## 4. 配置项

### 4.1 Qdrant 连接

```yaml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334              # gRPC
        collection-name: ad_agent_rag
        initialize-schema: true # 首次自动建 collection
        # api-key:                # 云托管 Qdrant 时填写
        # use-tls: false
```

### 4.2 Embedding（与 Chat 分开配置）

RAG 需要 **EmbeddingModel**，不能与对话模型混用同一套「只配了 chat」的配置。

#### 4.2.1 本地 ONNX（推荐，`rag-local` profile）

依赖 `spring-ai-starter-model-transformers`，无需外网 Embedding API。

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=rag-local"
```

详见 `scripts/embedding/README.md`。中文可选 `scripts/embedding/download-bge-small-zh.sh`。

#### 4.2.2 云端 OpenAI 兼容（`rag` profile）

```yaml
spring:
  ai:
    model:
      chat: openai
      embedding: openai
    openai:
      api-key: ${OPENAI_API_KEY}   # application-secret.yml
      base-url: https://api.openai.com
      chat:
        options:
          model: MiniMax-M3        # 或 gpt-4o-mini 等
      embedding:
        options:
          model: text-embedding-3-small
```

要点：

- **同一 collection 内向量维度必须固定**；换 embedding 模型需重建索引。
- MiniMax 对话（M3）与 embedding 模型不同；若用 MiniMax embedding，需确认其 OpenAI 兼容 embedding 端点与维度。
- API Key 仅放在 `application-secret.yml`（已 gitignore），勿提交仓库。

### 4.3 业务开关（建议，实现时添加）

```yaml
ad-agent:
  rag:
    enabled: true
    top-k: 5
    similarity-threshold: 0.65
```

`enabled=false` 时回退现有逻辑（如长期记忆按时间取最近 N 条、内容库全量拼接）。

## 5. RAG 数据模型

### 5.1 Document 与 metadata

每条可向量化内容对应 Spring AI 的一个 `Document`：

| metadata 字段 | 必填 | 说明 |
|-----------------|------|------|
| `user_id` | 是 | 多用户隔离；检索必须过滤 |
| `type` | 是 | `content` / `memory` / `skill` / `chat` |
| `source_id` | 推荐 | 业务主键，如 contentId、memory 条目 id |
| `name` | 可选 | 展示用标题 |
| `created_at` | 可选 | ISO 时间，审计与排序 |

正文（`Document` text）建议：

- **内容库**：`name + "\n" + summary`
- **长期记忆**：`summary` 单条
- **技能**：按段落或整篇 md（过大需分块）
- **聊天**：按 500～800 字分块，带 overlap

### 5.2 Collection

默认 collection 名：`ad_agent_rag`（可通过配置修改）。  
同一 collection 用 `type` + `user_id` 区分业务，避免为每种数据各起一个 collection（学习阶段足够）。

## 6. RAG 流程

```
用户问题 / 业务 query
    → EmbeddingModel.embed(query)
    → Qdrant similaritySearch（filter: user_id + type）
    → topK Document
    → 拼入 Prompt 或 RetrievalAugmentationAdvisor
    → ChatClient 生成
```

### 6.1 检索示例（手动注入 Prompt）

```java
SearchRequest request = SearchRequest.builder()
    .query(query)
    .topK(5)
    .similarityThreshold(0.65)
    .filterExpression("user_id == '" + userId + "' && type == 'content'")
    .build();
List<Document> hits = vectorStore.similaritySearch(request);
```

### 6.2 Advisor 方式（对话 Agent）

```java
Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .vectorStore(vectorStore)
        .topK(5)
        .similarityThreshold(0.65)
        .build())
    .queryAugmenter(ContextualQueryAugmenter.builder()
        .allowEmptyContext(true)
        .build())
    .build();

chatClient.prompt()
    .advisors(ragAdvisor)
    .advisors(a -> a.param(
        VectorStoreDocumentRetriever.FILTER_EXPRESSION,
        "user_id == '" + userId + "'"))
    .user(userInput)
    .call()
    .content();
```

## 7. 计划接入的业务场景（优先级）

| 优先级 | 场景 | 数据源 | 接入点 |
|--------|------|--------|--------|
| P0 | 内容库 RAG | `contents.json` | `CreativePromptSuggestService`、`MaterialCreativeAgentService` |
| P1 | 长期记忆语义检索 | `long_term_memory/{userId}.json` | `LongTermMemoryService.retrieveRelevantMemories`（已预留 `query` 参数） |
| P2 | 技能按需加载 | `classpath:skills/**/*.md` | 与 `markdown-skills` 互补，按 query 取 top 2～3 篇 |
| P3 | 聊天历史语义搜索 | `chat/sessions/*.json` | 跨会话召回；与隐私清除联动删向量 |

## 8. 索引更新策略（MVP）

数据量小，采用简单策略即可：

1. **全量重建**：按 `user_id` 删除向量 → 从 JSON 重新 `add`（启动时或 `contents.json` 保存后）。
2. **增量**（后续）：按 `source_id` upsert，避免全量重建。

内容库变更时：在 `ContentCatalogRepository.save()` 之后调用 `ContentRagIndexService.reindexUser(userId)`。

## 9. 安全与隐私

- 检索 **必须** 带 `user_id` 过滤，禁止全库无过滤搜索。
- `UserPrivacyTools` 清除长期记忆 / 聊天时，同步按 metadata 删除 Qdrant 中该用户向量。
- 向量库 **不存 API Key**；仅存业务文本与 metadata。
- 提交前勿将 Key 写入 `application.yml`；使用 `application-secret.yml`。

## 10. 实现路线图

| 阶段 | 内容 | 状态 |
|------|------|------|
| 文档与脚本 | 本文档 + `scripts/qdrant/start-qdrant.sh` | 已完成 |
| Content + Memory 方案 | [rag-content-memory-plan.html](./rag-content-memory-plan.html) | 已完成 |
| PR A | 基础设施：Qdrant 依赖、`RagVectorStoreService`、降级开关 | 待开发 |
| PR B | Memory RAG：`retrieveRelevantMemories` 语义检索、双写、隐私删向量 | 待开发 |
| PR C | Content RAG：索引 + `suggest-prompt` 接入 | 待开发 |

## 11. 故障排查

| 现象 | 可能原因 |
|------|----------|
| 启动连不上 Qdrant | 容器未启动；`port` 应为 **6334**（gRPC） |
| 检索结果为空 | `similarity-threshold` 过高；embedding 与索引模型不一致 |
| 维度错误 | 换 embedding 模型后未重建 collection |
| 跨用户内容泄漏 | 检索未加 `user_id` filter |

## 12. 参考

- [Spring AI — Qdrant](https://docs.spring.io/spring-ai/reference/api/vectordbs/qdrant.html)
- [Spring AI — RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [Qdrant 文档](https://qdrant.tech/documentation/)
