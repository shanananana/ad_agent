# 本地数据目录

运行时数据存放在此目录（默认 `./data`，即项目根目录下的 `data/`）。

## 结构

- **base/campaigns.json**：投放基础数据（计划、广告组、广告、素材），单文件（多用户时还有 `base/users/{userId}/campaigns.json`，见项目根 README）
- **performance/performance.json**：效果数据（展示、点击、CTR、消耗、ROI 等），按计划/广告组/广告/素材、渠道、年龄、日期（多用户时还有 `performance/users/{userId}/performance.json`）
- **long_term_memory/{userId}.json**：长期记忆，每个用户一个文件；内容为与用户习惯、投放偏好相关的摘要，经 LLM 判断后写入。写入时机由配置 `ad-agent.memory.immediate-long-term-flush` 控制：开=每轮结束立即写入，关=空闲 5 分钟后在下一轮开始时写入。用户可通过对话触发**清除长期记忆**（服务端删除对应 json，**不包含** performance 等业务数据）
- **chat/sessions/{sessionId}.json**：单会话聊天记录；**chat/users/{userId}/sessions.json** 为该用户的会话列表索引。清除聊天记录时会删会话文件并更新索引，并扫描 sessions 目录按文件内 userId 合并删除，避免漏删

应用启动时会从该目录读取；加计划、改策略会立即写入。

若未看到本目录，请从项目根执行 `mvn spring-boot:run` 后，数据会写入当前工作目录下的 `data/`。
