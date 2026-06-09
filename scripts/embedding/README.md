# 本地 Embedding（ONNX）

## 默认（无需下载）

Profile `rag-local` 使用 Spring AI 内置 **all-MiniLM-L6-v2**（384 维，英文为主）。

```bash
./scripts/qdrant/start-qdrant.sh
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=rag-local"
```

## 中文模型（可选）

```bash
./scripts/embedding/download-bge-small-zh.sh
```

按脚本输出修改 `application-rag-local.yml` 中的 `model-uri` / `tokenizer.uri`，并换 Qdrant collection 名（维度变化需新 collection）。

## Profile 对照

| Profile | Embedding | 向量库 |
|---------|-----------|--------|
| `secret`（默认） | 无 | 无 |
| `rag,rag-mock` | 伪向量（验证用） | 内存 |
| `rag-local` | 本地 ONNX | Qdrant |
| `rag` | OpenAI 兼容 API | Qdrant |
