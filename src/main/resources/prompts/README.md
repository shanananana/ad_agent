# 提示词资源（classpath:prompts/）

所有面向大模型的说明性文案放在此目录，Java 代码仅负责加载与占位符注入，见 `ClasspathPromptLoader`。
路径常量集中定义在 `com.example.adagent.prompt.PromptResourcePaths`（重命名文件时请同步修改该类）。

| 路径 | 用途 |
|------|------|
| `chat-system.txt` | 主对话 ChatClient 默认 system |
| `bidding-system.txt` | 自动调价 ChatClient system |
| `creative-system.txt` | 文生图描述推荐 ChatClient system |
| `intent-with-memory.txt` | 意图识别 user 模板（`{userInput}` 等） |
| `intent-simple.txt` | 无会话/记忆版意图模板（与带记忆版语义对齐，供扩展或对照；当前运行时仅用 with-memory） |
| `creative-suggest-user.txt` | 智能生成画面描述 user 模板 |
| `long-term-memory-judge.txt` | 长期记忆是否写入判断 |
| `bidding-coefficient-user.txt` | 调价 LLM user 主模板 |
| `bidding/plan-intro.txt` | 调价 user 中计划头（`{campaignId}` `{nameClause}`） |
| `bidding-json-example.txt` | 调价 JSON 输出示例片段（拼入主模板） |
| `orchestrator/user-message.txt` | 编排器增强 user 消息骨架 |
| `orchestrator/user-id-provided.txt` / `user-id-missing.txt` | 有无 userId 时的说明段 |
| `placement/*.txt` | 版位 → 画幅说明（`other.txt` 含 `{placementCode}`） |
| `planning/cot-*.txt` / `react-*.txt` | 规划层展示用步骤与 reasoning 文案 |

占位符统一为 `{variable}`，与 Spring AI `PromptTemplate` 一致。
