# 自动调价助手（B × α）设计

## 目标

- **基础出价 B**：按 **人群 × 时段（小时槽）× 设备** 配置，存 `data/bid/base_bid_model.json`（运行时生成，见 `.gitignore`）。
- **动态系数 α**：由 **定时任务** 读取 **效果快照** `effect_snapshot.json`，调用 **LLM** 建议新 α，经 **护栏** 后写入 `coefficients.json`。
- **生效价**：`effective = B × α`，供未来 **实时竞价层** 在「不改原模型」的前提下仅做系数缩放；**本仓库不实现 RTB**，提供 `BidStrategyService#getEffectiveBaseBid` 等 API 预留。

## 链路

1. `EffectSnapshotGenerator` 可生成 **合成效果**（学习项目）；`POST /regenerate-snapshot` 仅刷新快照。
2. `BidCoefficientScheduledJob` 按 `ad-agent.bidding.cron` 触发（可用 `ad-agent.bidding.enabled=false` 关闭）。
3. `BidCoefficientLlmService` 使用 **`biddingChatClient`**（无工具，见 `ChatClientConfig`），要求 LLM 输出 JSON：`rationale`（须分节写清 **上调 / 下调 / 维持与小结**，并引用 ROI、CTR 等具体原因）+ `entries[]`（`audience/hourSlot/device/alpha`）。任务日志中另有程序生成的 **涨跌概要**（旧 α→新 α）与 rationale 互补。
4. **护栏**：`alpha ∈ [alpha-min, alpha-max]`，且相对上一版变化不超过 `max-relative-change`。
5. 解析失败 **不覆盖** `coefficients.json`，仅写失败日志到 `coefficient_job_log.json`。

## 配置

见 `application.yml` 前缀 `ad-agent.bidding`：`enabled`、`cron`、`alpha-min`、`alpha-max`、`max-relative-change`、`job-log-max-entries`。

## 存储

- **全局（兼容）**：`data/bid/*.json`。
- **按计划**：`data/bid/campaigns/{计划ID}/` 下同样四套文件；页面从计划进入后只操作该目录。

## API 与页面

- `GET /api/ad-agent/bid-strategy/campaigns?userId=`：计划列表。
- `GET /api/ad-agent/bid-strategy/campaigns/{id}/detail?userId=`：计划详情 + **performance 汇总与最近明细**。
- `POST .../sync-performance?userId=`：按当前基础数据为该计划追加 7 天合成效果（便于学习演示）。
- `GET .../campaigns/{id}/overview?userId=`：该计划的 B、α、B×α 与快照网格。
- `GET .../campaigns/{id}/job-log?limit=`
- `POST .../campaigns/{id}/run-job` body 可选 `{ "regenerateSnapshot": true }`
- `POST .../campaigns/{id}/regenerate-snapshot`
- 兼容全局：`GET/POST /overview`、`/run-job` 等（根目录 `data/bid`）。
- 静态页：`/bid-strategy.html`（列表 → 计划详情含效果 + 调价）。

## 与 RTB 的关系

- **LLM 仅在周期层调用**；参竞路径应 **O(1) 查表** 读 α，不调用 LLM。
- 底层 RTB 模型输出后再乘 α 的细节由接入方实现；注意与 **pacing/预算** 控制器的协调，避免双环振荡。
