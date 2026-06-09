# 大模型配置（OpenAI 兼容）

对话与文生图统一使用 Spring AI **OpenAI 兼容**客户端，只改配置即可切换厂商，无需 Alibaba / DashScope 依赖。

向量库与 RAG（Qdrant、Embedding、索引约定）见 [qdrant-vector-rag.md](./qdrant-vector-rag.md)。

## 配置项

| 用途 | 属性 |
|------|------|
| API 地址 | `spring.ai.openai.base-url`（**不要**带 `/v1` 后缀）或 `OPENAI_BASE_URL` |
| 对话模型 | `spring.ai.openai.chat.options.model` 或 `OPENAI_MODEL` |
| API Key | `spring.ai.openai.api-key`（`application-secret.yml`） |
| 文生图（可选） | `spring.ai.model.image=openai` + `spring.ai.openai.image.options.model` |

## 示例：MiniMax 对话

```yaml
spring:
  ai:
    model:
      chat: openai
      image: none
    openai:
      api-key: sk-...
      base-url: https://api.minimaxi.com
      chat:
        options:
          model: MiniMax-M3
```

## 示例：OpenAI 对话 + 出图

```yaml
spring:
  ai:
    model:
      chat: openai
      image: openai
    openai:
      api-key: sk-...
      base-url: https://api.openai.com/v1
      chat:
        options:
          model: gpt-4o-mini
      image:
        options:
          model: dall-e-3
```
