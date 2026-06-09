#!/usr/bin/env bash
# 本地启动 Qdrant（向量库 / RAG）。详见 docs/ai_design_doc/qdrant-vector-rag.md
set -euo pipefail

CONTAINER_NAME="${QDRANT_CONTAINER_NAME:-ad-agent-qdrant}"
IMAGE="${QDRANT_IMAGE:-qdrant/qdrant:latest}"
HTTP_PORT="${QDRANT_HTTP_PORT:-6333}"
GRPC_PORT="${QDRANT_GRPC_PORT:-6334}"
VOLUME="${QDRANT_VOLUME:-ad_agent_qdrant_storage}"

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
    echo "Qdrant 已在运行: $CONTAINER_NAME"
    echo "Dashboard: http://localhost:${HTTP_PORT}/dashboard"
    exit 0
  fi
  echo "启动已有容器: $CONTAINER_NAME"
  docker start "$CONTAINER_NAME"
else
  echo "创建并启动 Qdrant: $CONTAINER_NAME"
  docker run -d --name "$CONTAINER_NAME" \
    -p "${HTTP_PORT}:6333" \
    -p "${GRPC_PORT}:6334" \
    -v "${VOLUME}:/qdrant/storage" \
    "$IMAGE"
fi

echo "HTTP : http://localhost:${HTTP_PORT}"
echo "gRPC : localhost:${GRPC_PORT}  (Spring AI 默认)"
echo "Dashboard: http://localhost:${HTTP_PORT}/dashboard"
