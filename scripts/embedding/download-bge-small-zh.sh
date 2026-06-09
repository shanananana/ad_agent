#!/usr/bin/env bash
# 下载 BGE 中文小模型 ONNX 到 local-models/（已 gitignore）
# 用法后改 application-rag-local.yml 中 model-uri / tokenizer.uri 为 file: 路径
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../" && pwd)"
TARGET="$ROOT/local-models/bge-small-zh-v1.5-onnx"
mkdir -p "$TARGET"

BASE="https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main"

echo "下载到: $TARGET"

curl -fsSL "$BASE/onnx/model.onnx" -o "$TARGET/model.onnx"
curl -fsSL "$BASE/tokenizer.json" -o "$TARGET/tokenizer.json"

echo "完成。请在 application-rag-local.yml 中设置:"
echo "  spring.ai.embedding.transformer.onnx.model-uri: file:$TARGET/model.onnx"
echo "  spring.ai.embedding.transformer.tokenizer.uri: file:$TARGET/tokenizer.json"
echo "  spring.ai.embedding.transformer.onnx.model-output-name: last_hidden_state"
echo "  spring.ai.vectorstore.qdrant.collection-name: ad_agent_rag_bge_zh"
