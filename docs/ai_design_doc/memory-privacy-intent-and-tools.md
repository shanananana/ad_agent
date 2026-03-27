# 长期记忆与聊天记录清除：意图分支与工具设计

## 1. 背景与目标

用户可能希望：

- **仅清除长期记忆**：删除跨会话写入的偏好/习惯摘要文件（`data/long_term_memory/{userId}.json`），不影响各会话聊天文件。
- **仅清除聊天记录**：删除该用户在本地持久化的全部会话文件及索引，并清理进程内短期记忆（含当前 `sessionId`）。
- **两者都清除**：一次性执行上述两类删除。

本设计通过 **意图识别新增分支** + **Spring AI `@Tool`** 实现；编排器在识别到 `needsTool=true` 后走既有 ReAct 规划，由模型在增强 Prompt 中调用对应工具（工具名与 `ReActService` 中声明一致）。

## 2. 意图定义

| intentType | needsTool | 典型用户说法 |
|------------|-----------|----------------|
| `INTENT_CLEAR_LONG_TERM_MEMORY` | true | 清除长期记忆、删掉我的偏好/习惯、忘记我之前说的投放习惯 |
| `INTENT_CLEAR_CHAT_HISTORY` | true | 清空聊天记录、删除所有对话、删掉历史聊天 |
| `INTENT_CLEAR_ALL_USER_MEMORY` | true | 全删掉、记忆和聊天都清除、隐私数据全部清空 |

与 `INTENT_OTHER` 的边界：

- 纯闲聊、未表达删除/清除数据意愿 → `INTENT_OTHER`，`needsTool=false`。
- 若同时提到「长期记忆」与「聊天」两类对象，或明确「全部清除」，判为 `INTENT_CLEAR_ALL_USER_MEMORY`。

## 3. 工具定义

### 3.1 `clearUserLongTermMemory(String userId)`

- **作用**：删除 `DataPathConfig#getLongTermMemoryPath(userId)` 对应文件（若存在）。
- **约束**：`userId` 为空则返回说明文案，不执行删除（避免误删 `default` 或匿名路径）。
- **实现**：`LongTermMemoryRepository#deleteForUser`，与 `append` 相同 userId 粒度加锁，避免与并发写入冲突。

### 3.2 `clearUserChatHistory(String userId, String currentSessionId)`

- **作用**：

  1. 枚举 `chatHistoryRepository.listSessionsByUser(userId)` 中全部 `sessionId`，逐个调用既有 `AdChatSessionService#deleteSession`（删会话文件、更新用户索引、清短期记忆、`sessionToUserMap`）。
  2. 将 **当前请求上下文中的 `currentSessionId`** 并入集合再删一次（幂等），覆盖「仅 bind 用户、尚未出现在索引中的会话」等边角：至少清掉当前会话内存与磁盘文件（若存在）。

- **约束**：`userId` 为空则不执行批量删除（与长期记忆工具一致）。
- **`currentSessionId`**：允许为空字符串；非空时应与编排器注入的【当前会话ID】一致。

### 3.3 组合意图 `INTENT_CLEAR_ALL_USER_MEMORY`

- 规划层 `requiredTools` 同时包含 `clearUserLongTermMemory` 与 `clearUserChatHistory`；模型在一轮内可顺序调用两个工具。
- **顺序建议**：文档与 System 提示中建议先清长期记忆再清聊天（或反之均可，二者无文件级依赖）。

## 4. 数据与安全说明

- **userId 来源**：与现有投放工具一致，来自编排器在增强 Prompt 中注入的【当前用户ID】；模型调用工具时必须传入该值，**禁止**编造其他用户的 ID（System 提示中强调）。
- **与 `default` 用户**：未绑定 `userId` 时工具拒绝执行删除，避免误操作共享的默认数据视图。
- **删除后当轮写入**：聊天文件删除后，`ChatHistoryRepository#appendMessage` 在会话文件不存在时直接返回，不会自动重建；用户后续新开会话或走 `saveNewSession` 流程会重新创建文件。

## 5. 服务端强制执行（与工具的关系）

仅依赖模型调用 `clearUserLongTermMemory` / `clearUserChatHistory` 时，容易出现 **模型未调工具或参数错误**，表现为「回复说已清除，磁盘文件仍在」。

因此 **`AdAgentOrchestrator` 在意图识别为三类隐私意图之一且 `needsTool=true` 时，在调用工具链路之前直接在服务端执行删除**（与工具逻辑一致），保证 `long_term_memory` 与 `chat/sessions` 下的文件被真实删除。`@Tool` 仍保留，便于观测或其它入口复用。

**清除成功后的前端刷新**：磁盘删除成功时由流式接口 **`POST /api/ad-agent/stream-with-thinking`** 在适当时机追加 SSE `event: client`，`data: {"reload":true}`。静态页 `chat.html` 收到后清除本地保存的 `sessionId` 并 `location.reload()`。

清除聊天时，`ChatHistoryRepository#collectSessionIdsForUser` 除读取用户索引外，会 **扫描 `data/chat/sessions/*.json`**，按文件内 `userId` 合并会话 ID，避免索引与磁盘不一致导致漏删。

## 6. 代码改动清单（实现对照）

| 模块 | 改动 |
|------|------|
| `IntentRecognitionService` | 两处 Prompt 增加上述意图说明；`parseIntentByKeywords` 增加关键词兜底 |
| `ReActService` | `INTENT_TO_TOOLS` 映射三个 intent → 工具名列表 |
| `ChatClientConfig` | `defaultTools` 注册 `UserPrivacyTools`；`DEFAULT_SYSTEM` 增加工具使用说明 |
| `LongTermMemoryRepository` | `deleteForUser(String userId)` |
| `ChatHistoryRepository` | `collectSessionIdsForUser`：索引 + 目录扫描 |
| `AdChatSessionService` | `deleteAllChatHistoryForUser` 使用上述集合 |
| `UserPrivacyTools`（新） | 两个 `@Tool` 方法 |
| `AdAgentOrchestrator` | `runServerSidePrivacyClearIfApplicable` + `buildEnhancedPrompt` 补充说明 |

## 7. Bean 依赖说明

`UserPrivacyTools` 与 `AdAgentOrchestrator` 对 `AdChatSessionService` 使用 `@Lazy` 注入，以打破与 `ChatClient` 工具链路的循环依赖。

## 8. 后续可选增强

- 服务端鉴权与审计日志（操作人、目标 userId、IP）。
- 管理端 HTTP API 与工具双通道，工具仅对可信身份开放。
- 「仅删除当前会话」与「删除用户全部会话」在意图上再细分（当前「聊天记录」按「该用户全部会话」处理，与产品确认一致）。
