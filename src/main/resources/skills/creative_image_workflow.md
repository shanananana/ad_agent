---
id: creative_image_workflow
name: 广告素材文生图流程
description: 用户询问「怎么做文生图」「素材出图步骤」「如何生成广告图」或要在对话里帮用户完成「先拿数据再描述再出图」时使用。先调用本工具阅读全文，再严格按下列顺序调用其它工具。
---

# 对话内完成文生图（工具顺序）

下列 **工具名** 须按顺序使用（前一步有结果再进下一步）。**不要**在对话里编造未经验证的接口路径或参数。

## 1. 确认用户与计划上下文

- 调用 **`queryBaseData`**：`userId` 用当前对话中的用户 ID；若用户已指定计划，传 `campaignId`，否则可先不传以列出计划。
- 从返回中确认目标 **广告组 ID**（`adGroupId`）及 **计划 ID**（`campaignId`）。若用户未说明，先简短追问再往下执行。

## 2. 得到画面描述（prompt）——由模型完成，不单独调「写描述」工具

- **若用户已给出可直接出图的文字描述**：把用户原文整理后作为下一步的 `prompt`（过长需提醒压缩）。
- **若用户没有现成描述**：结合上一步 **`queryBaseData`** 返回的计划/广告/素材与效果上下文，**自行组织**一段完整、可执行的文生图画面描述（可融入版位、卖点、调性等）；必要时可追问用户一句再定稿。**不要**假设存在单独的「自动生成描述」工具。
- 若有结构化任务 JSON（如 `task=material_image`），其中的 `userPrompt`、`contentId`、`placement`、`title` 等字段须融入或对齐你最终写的 `prompt`。

## 3. 调用通用文生图工具

- 调用 **`generateImageFromPrompt`**：将你确定的 **`prompt` 字符串** 作为第一个参数传入。
- **`persist`**：用户需要写入全局素材目录时为 `true`，且**必须**同时传 **`userId`**（与当前用户一致）。
- **`title`、`contentId`、`placement`**：按任务或用户意图选填。
- 成功时返回 JSON 中含 `imageUrl` 或 `creativeId` / `persistedPath`（不含大段 base64）。向用户说明如何查看图片，**不要**在正文里写出工具名。

## 与页面操作的关系（供口头说明）

- **`chat.html` → 素材生成** Tab **「一键走对话 Agent（技能驱动）」**：调用 `POST /api/ad-agent/creative/material-agent-run`，请求体仅含 userId、campaignId、adGroupId 等；服务端将 **`task`=`material_image`** 的 JSON 交给主对话模型，由模型先读本技能再调 **`queryBaseData`** 与 **`generateImageFromPrompt`**（**不**跳转对话 Tab）。
- 分步调试仍可用 REST：**智能生成描述**（`suggest-prompt`）、**仅生成图片**（`generate`）。

## 前置条件

- 文生图依赖配置：`spring.ai.model.image=dashscope` 与有效的 DashScope `api-key`。若工具返回未启用类错误，告知用户检查配置，不要重试出图。
