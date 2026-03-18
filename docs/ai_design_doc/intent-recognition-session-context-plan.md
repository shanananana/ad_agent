# 意图识别注入会话记录 - 代码修改计划

## 目标
在意图识别时注入「最近对话」上下文，使模型能把用户对上一轮追问的简短确认（如「需要」「好的」「可以」「创建」）正确识别为对应意图（如 INTENT_ADD_CAMPAIGN），而不是判成 INTENT_OTHER。

---

## 一、涉及文件

| 文件 | 改动类型 |
|------|----------|
| `IntentRecognitionService.java` | 修改：新增会话上下文参数与 prompt 段落 |
| `AdAgentOrchestrator.java` | 修改：在调用意图识别前取短期上下文并传入 |

---

## 二、具体修改项

### 1. IntentRecognitionService（`agent/perception/IntentRecognitionService.java`）

**1.1 方法签名**

- 保留现有重载：
  - `recognizeIntent(String userInput)` → 内部调 `recognizeIntent(userInput, null, null)`。
  - `recognizeIntent(String userInput, String longTermContext)` → 内部调 `recognizeIntent(userInput, longTermContext, null)`。
- 新增三参数方法（供编排器调用）：
  - `recognizeIntent(String userInput, String longTermContext, String conversationContext)`。
- 内部实现统一走三参数：`conversationContext` 为 null 或 blank 时按「无最近对话」处理，与现有一致。

**1.2 Prompt 模板（intentWithMemoryPromptTemplate）**

- 在「用户输入」之前增加可选段落 **【最近对话】**：
  - 占位符：`{conversationContext}`。
  - 若 `conversationContext` 为空，则填入固定文案如「（无最近对话）」。
- 在模板的说明中增加一条规则（可放在「不需要工具」说明附近）：
  - 若存在【最近对话】，且用户当前输入是对上一轮助手提问的**简短确认/肯定**（例如「需要」「好的」「可以」「行」「创建」「确认」等），则必须结合最近对话判断意图：
    - 若上一轮助手在询问「是否创建计划」「是否按某方式创建」等，且用户表示肯定，则 `intentType` 填 `INTENT_ADD_CAMPAIGN`，`needsTool` 填 `true`。
    - 同理，若上一轮在问「是否查询某计划效果」等且用户肯定，则填对应查询类意图。
  - 避免仅因用户输入过短就判为 `INTENT_OTHER`。

**1.3 调用 LLM 时的参数**

- `create(Map.of("userInput", ..., "longTermContext", ..., "conversationContext", ...))`，其中 `conversationContext` 由方法参数传入，为空时用 `"（无最近对话）"` 或等价文案。

**1.4 无会话场景兼容**

- 当 `conversationContext == null || conversationContext.isBlank()` 时，传入模板的 `conversationContext` 使用「（无最近对话）」或空段落，保证单轮/无 session 场景行为与现在一致。

---

### 2. AdAgentOrchestrator（`agent/AdAgentOrchestrator.java`）

**2.1 统一在意图识别前获取短期上下文**

- 在三条执行路径中，都在调用 `recognizeIntent` **之前**先取短期上下文，再传入意图识别：
  - `execute(String sessionId, ...)`
  - `executeStream(String sessionId, ...)`
  - `executeStreamWithThinking(String sessionId, ...)`

**2.2 具体顺序（以 execute 为例）**

1. `longTermContext = memoryService.getLongTermContext(userId, userInput)`
2. `shortTermContext = memoryService.getShortTermContext(sessionId)`（提前到此处）
3. `intentResult = intentRecognitionService.recognizeIntent(userInput, longTermContext, shortTermContext)`
4. 后续逻辑不变，`buildEnhancedPrompt(..., shortTermContext, ...)` 仍使用同一 `shortTermContext` 变量。

**2.3 调用处修改**

- 将三处：
  - `intentRecognitionService.recognizeIntent(userInput, longTermContext)`
- 改为：
  - 先在该方法内取 `shortTermContext`（若该路径尚未提前取，则先增加 `shortTermContext` 的获取），再调用：
  - `intentRecognitionService.recognizeIntent(userInput, longTermContext, shortTermContext)`

即：三条路径都「先取 shortTermContext，再调 recognizeIntent(..., shortTermContext)」，且后面 `buildEnhancedPrompt` 继续用这份 `shortTermContext`，不重复取即可。

---

## 三、行为与兼容性

- **有会话且存在最近对话**：意图识别能结合「上一轮助手问是否创建」+ 用户「需要」→ 判为 INTENT_ADD_CAMPAIGN，needsTool=true，后续正常走加计划或追问逻辑。
- **无会话或新会话**：`shortTermContext` 为空，传入的 `conversationContext` 为空，模板中显示「（无最近对话）」或等价内容，行为与当前一致。
- **其他入口**：若仅调用 `recognizeIntent(userInput)` 或 `recognizeIntent(userInput, longTermContext)`，通过重载转三参数且 conversationContext=null，无需改调用方。

---

## 四、实现顺序建议

1. **IntentRecognitionService**  
   - 增加三参数 `recognizeIntent(userInput, longTermContext, conversationContext)` 及内部实现。  
   - 两参、无参重载改为委托到三参（conversationContext 为 null）。  
   - 在 `intentWithMemoryPromptTemplate` 中增加 `{conversationContext}` 段落与「结合最近对话识别简短确认」的说明。  
   - 调用 `create(...)` 时传入 `conversationContext`（空时用「（无最近对话）」）。

2. **AdAgentOrchestrator**  
   - 在 `execute` 中：先取 `shortTermContext`，再调 `recognizeIntent(userInput, longTermContext, shortTermContext)`，其余不变。  
   - 在 `executeStream`、`executeStreamWithThinking` 中做同样调整：先取 `shortTermContext`，再传入 `recognizeIntent`。

3. **自测建议**  
   - 场景 1：上一轮助手问「是否需要我立即为您创建这个计划？」→ 用户回「需要」→ 意图应为 INTENT_ADD_CAMPAIGN，needsTool=true。  
   - 场景 2：无历史对话，用户直接说「需要」→ 意图可为 INTENT_OTHER（或保持现状）。  
   - 场景 3：用户说「加一个计划」等原有话术 → 仍为 INTENT_ADD_CAMPAIGN，与现在一致。

---

## 五、可选后续（本方案不包含）

- 对「确认创建」做更细的规则（例如从上一轮抽取计划名、预算等参数并传入 addCampaign），可在本方案落地后再做。
- 若短期上下文过长，可只取最近 N 轮或最近 K 字符再传入意图识别，以控制 token 与稳定性。

以上为完整代码修改计划，确认后再按此开发即可。
