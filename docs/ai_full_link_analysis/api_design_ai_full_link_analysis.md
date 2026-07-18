# AI 全链路分析 — API 设计

> **需求来源：** [prd](./prd_ai_full_link_analysis.md) · [tech](./tech_design_ai_full_link_analysis.md) · [db](./db_design_ai_full_link_analysis.md)  
> **功能短名：** `ai_full_link_analysis`  
> **状态：** DRAFT — 对外契约；实现与 Task 另项点名  
> **配套前端修改文档：** [docs/ai_observability_frontend.md](../ai_observability_frontend.md)

---

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| 只读 | MVP 仅查询与聚合，无写入/删除业务 API（清理走定时任务） |
| 研发向 | 不面向求职者主站；需 Admin 凭证 |
| HTTP 语义 | 用状态码表达成败；body 为资源 JSON（与现有 `/health`、`/api/login` 风格一致，不用统一 `code/msg` 包装） |
| 时区 | 时间查询与响应一律 **ISO-8601 UTC**（如 `2026-07-18T03:00:00.000Z`） |
| 隐私 | 响应不含 prompt / JD / 简历原文；`attributes` 仅返回已落库的安全字段 |

---

## 2. 鉴权

> **增量（登录功能，已拍板）：** 观测鉴权**仅** ADMIN JWT；**去除** `X-Obs-Admin-Token` / `OBS_ADMIN_TOKEN`。详见 [login/API设计.md](../login/API设计.md) §2.3。

| 项 | 约定 |
|----|------|
| 开关 | `app.observability.enabled=true` |
| 凭证 | `Authorization: Bearer <jwt>`，且 JWT `role=ADMIN`（含 `pv` 校验） |
| 无效/缺失 | `401` `UNAUTHORIZED` |
| USER JWT | `403` `FORBIDDEN` |
| 求职者 WS | 使用 USER JWT（见 login API），不用观测头 |

错误 body 统一：

```json
{
  "error": "UNAUTHORIZED",
  "message": "invalid admin token"
}
```

常见 `error`：`UNAUTHORIZED` | `FORBIDDEN` | `NOT_FOUND` | `BAD_REQUEST` | `OBS_DISABLED` | `OBS_ADMIN_NOT_CONFIGURED`

---

## 3. REST API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/observability/traces/{traceId}` | 单 trace 详情 + span 时间线 |
| GET | `/api/observability/traces` | trace 列表（筛选/分页） |
| GET | `/api/observability/stats/tokens` | Token / 成本聚合 |
| GET | `/api/observability/stats/rag` | RAG 效果聚合 |
| GET | `/api/observability/stats/tools` | Tool（Skill）成功率聚合 |
| GET | `/api/observability/stats/agents` | Agent / Graph 节点健康度聚合 |
| GET | `/api/observability/status` | 观测模块开关与队列粗状态（运维探活） |

---

## 4. 接口明细

### 4.1 查询单条 Trace

`GET /api/observability/traces/{traceId}`

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| traceId | String | 是 | `ai_trace.trace_id` |

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| includeAttributes | boolean | 否 | 默认 `false`；`true` 时 span 带 `attributes` |

**响应 `200`**

```json
{
  "traceId": "a1b2c3d4e5f6",
  "scene": "INTERVIEW",
  "sessionId": "uuid-interview",
  "wsSessionId": "ws-xxx",
  "userId": null,
  "status": "OK",
  "startedAt": "2026-07-18T03:00:00.000Z",
  "endedAt": "2026-07-18T03:12:00.000Z",
  "errorSummary": null,
  "droppedChildHint": 0,
  "spans": [
    {
      "spanId": "s1",
      "parentSpanId": null,
      "spanType": "ROOT",
      "name": "root.start_interview",
      "status": "OK",
      "startedAt": "2026-07-18T03:00:00.000Z",
      "endedAt": "2026-07-18T03:12:00.000Z",
      "durationMs": 720000,
      "agent": null,
      "node": null,
      "model": null,
      "toolName": null,
      "promptTokens": null,
      "completionTokens": null,
      "totalTokens": null,
      "costAmount": null,
      "costCurrency": null,
      "ragCandidateCount": null,
      "ragEmpty": null,
      "ragHit": null,
      "ragReranked": null,
      "ragRerankFallback": null,
      "errorType": null,
      "errorMessage": null,
      "attributes": null
    },
    {
      "spanId": "s2",
      "parentSpanId": "s1",
      "spanType": "AGENT",
      "name": "agent.analyze_jd",
      "status": "OK",
      "startedAt": "2026-07-18T03:00:01.000Z",
      "endedAt": "2026-07-18T03:00:04.000Z",
      "durationMs": 3000,
      "agent": "JdAnalysisAgent",
      "node": "analyze_jd",
      "model": null,
      "toolName": null,
      "promptTokens": null,
      "completionTokens": null,
      "totalTokens": null,
      "costAmount": null,
      "costCurrency": null,
      "ragCandidateCount": null,
      "ragEmpty": null,
      "ragHit": null,
      "ragReranked": null,
      "ragRerankFallback": null,
      "errorType": null,
      "errorMessage": null,
      "attributes": null
    }
  ]
}
```

`spans` 按 `startedAt ASC, spanId ASC` 排序（扁平列表；客户端可用 `parentSpanId` 建树）。

**错误**

| HTTP | error |
|------|--------|
| 404 | `NOT_FOUND` |
| 401 | `UNAUTHORIZED` |
| 503 | `OBS_DISABLED` / `OBS_ADMIN_NOT_CONFIGURED` |

---

### 4.2 Trace 列表

`GET /api/observability/traces`

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| from | String (ISO-8601) | 是 | 起始时间（含），按 `startedAt` |
| to | String (ISO-8601) | 是 | 结束时间（不含） |
| scene | String | 否 | `CHAT` / `SKILL` / `INTERVIEW` / `UPLOAD` / `OTHER` |
| sessionId | String | 否 | 面试会话 id |
| userId | Long | 否 | 按 `ai_trace.user_id` 精确筛选（登录功能增量） |
| status | String | 否 | `RUNNING` / `OK` / `ERROR` / `CANCELLED` |
| page | int | 否 | 从 0 起，默认 0 |
| size | int | 否 | 默认 20，最大 100 |

**响应 `200`**

```json
{
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6,
  "content": [
    {
      "traceId": "a1b2c3d4e5f6",
      "scene": "INTERVIEW",
      "sessionId": "uuid-interview",
      "status": "OK",
      "startedAt": "2026-07-18T03:00:00.000Z",
      "endedAt": "2026-07-18T03:12:00.000Z",
      "errorSummary": null
    }
  ]
}
```

列表项**不含** spans（避免大包）。

**校验：** `from`/`to` 缺失或 `from >= to` → `400 BAD_REQUEST`；时间窗最大跨度建议 31 天（超限 `400`，待确认）。

---

### 4.3 Token / 成本统计

`GET /api/observability/stats/tokens`

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| from | String | 是 | |
| to | String | 是 | |
| scene | String | 否 | 通过 trace 关联过滤（实现可 join `ai_trace`） |
| groupBy | String | 否 | 逗号分隔，允许：`agent`,`node`,`model`,`scene`；默认 `agent,model` |

**响应 `200`**

```json
{
  "from": "2026-07-01T00:00:00.000Z",
  "to": "2026-07-18T00:00:00.000Z",
  "groupBy": ["agent", "model"],
  "rows": [
    {
      "agent": "JdAnalysisAgent",
      "node": null,
      "model": "qwen-plus",
      "scene": null,
      "llmCalls": 42,
      "sumPromptTokens": 120000,
      "sumCompletionTokens": 36000,
      "sumTotalTokens": 156000,
      "sumCostAmount": 1.23456789,
      "costCurrency": "CNY",
      "usageMissingCount": 2
    }
  ],
  "totals": {
    "llmCalls": 42,
    "sumPromptTokens": 120000,
    "sumCompletionTokens": 36000,
    "sumTotalTokens": 156000,
    "sumCostAmount": 1.23456789,
    "costCurrency": "CNY",
    "usageMissingCount": 2
  }
}
```

仅统计 `spanType=LLM`。`usageMissingCount`：`prompt_tokens IS NULL` 的次数。

---

### 4.4 RAG 效果统计

`GET /api/observability/stats/rag`

**查询参数：** `from`、`to`（必填）；`scene`（可选）

**响应 `200`**

```json
{
  "from": "2026-07-01T00:00:00.000Z",
  "to": "2026-07-18T00:00:00.000Z",
  "retrieves": 100,
  "emptyCount": 10,
  "hitCount": 90,
  "emptyRate": 0.1,
  "hitRate": 0.9,
  "rerankCount": 80,
  "rerankRate": 0.8,
  "rerankFallbackCount": 3,
  "avgDurationMs": 120.5
}
```

口径对齐库表设计 / 技术方案：`empty`/`hit`/`reranked` 基于 `ai_span` RAG 冗余列。

---

### 4.5 Tool（Skill）成功率

`GET /api/observability/stats/tools`

**查询参数：** `from`、`to`（必填）；可选 `toolName`

**响应 `200`**

```json
{
  "from": "2026-07-01T00:00:00.000Z",
  "to": "2026-07-18T00:00:00.000Z",
  "rows": [
    {
      "toolName": "QuickQuizSkill",
      "calls": 20,
      "okCount": 18,
      "errorCount": 2,
      "successRate": 0.9,
      "avgDurationMs": 800.0
    }
  ]
}
```

仅 `spanType=TOOL`。

---

### 4.6 Agent / 节点健康度

`GET /api/observability/stats/agents`

**查询参数：** `from`、`to`（必填）；可选 `node`、`agent`；`groupBy` 默认 `node`

**响应 `200`**

```json
{
  "from": "2026-07-01T00:00:00.000Z",
  "to": "2026-07-18T00:00:00.000Z",
  "groupBy": ["node"],
  "rows": [
    {
      "node": "analyze_jd",
      "agent": "JdAnalysisAgent",
      "runs": 50,
      "okCount": 49,
      "errorCount": 1,
      "successRate": 0.98,
      "avgDurationMs": 2500.0
    }
  ]
}
```

仅 `spanType=AGENT`。P50/P95 MVP **不返回**（可后续加）；先用 avg。

---

### 4.7 观测模块状态

`GET /api/observability/status`

**鉴权：** 同样需要 Admin Token（避免泄露队列信息）；若希望公网探活，可另议（待确认，默认需鉴权）。

**响应 `200`**

```json
{
  "enabled": true,
  "adminConfigured": true,
  "retainDays": 14,
  "queueCapacity": 10000,
  "queueSize": 12,
  "droppedSpansTotal": 0
}
```

`droppedSpansTotal` 为进程内计数（重启清零），非库表强一致。

---

## 5. WebSocket 契约增量（可选、向后兼容）

求职者主流程**不依赖**观测 API。为便于「用户报障时带上 traceId」，在 `app.observability.enabled=true` 时，出站消息**可选**增加字段：

### 5.1 出站字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `traceId` | String | 否 | 当前上下文 trace；未启用观测时不出现（`NON_NULL`） |

适用于现有 `WsOutboundMessage` 扩展，**不新增 type**。

### 5.2 推荐回传时机

| 出站 type | 是否带 `traceId` | 说明 |
|-----------|------------------|------|
| `interview_started` | 是 | 整场面试 root trace，最有用 |
| `error` | 是（若上下文有） | 排障 |
| `chat` / `upload_result` | 是 | 单次消息 trace |
| `phase` / `question` / … | 否（默认） | 降低噪声；需要时可全开（配置项待确认） |

**示例**

```json
{
  "type": "interview_started",
  "content": "面试已开始",
  "sessionId": "uuid-interview",
  "traceId": "a1b2c3d4e5f6",
  "data": null
}
```

入站消息**不要求**客户端传 `traceId`（服务端分配/复用）。

---

## 6. Security / 路由约定

| 路径 | Security |
|------|----------|
| `/api/observability/**` | 不走「鉴权关闭则全放行」的匿名可写语义；**始终**校验 `X-Obs-Admin-Token`（或在 `enabled=false` / token 未配置时直接 503） |
| 静态可选页 `/obs.html` | 可匿名加载 HTML/JS；**数据仍走上述 API + Token**（Token 由运维本地粘贴，不写死进仓库） |

---

## 7. 非目标 API（本阶段不做）

- 删除/修正 span、手动重放
- 告警 webhook 订阅
- 导出 CSV 专用接口（可用前端拉 stats/traces 自行导出）
- 求职者侧「全链路分析报告」类 WS type
- OpenTelemetry OTLP 导出端点

---

## 8. 版本与兼容

- 路径暂不加 `/v1`（与现有 `/api/login` 一致）；若未来破坏性变更再引入 `/api/v1/observability/**`。
- WS `traceId` 为可选字段，老前端忽略即可。

---

## 9. 待确认

- [是] 列表/统计时间窗最大跨度是否锁定 31 天？
- [允许] `/api/observability/status` 是否允许无 Token 访问？
- [都带] WS 是否默认仅 `interview_started`/`error`/`chat` 带 `traceId`，还是全量出站都带？
- [提供] 是否提供可选页面 `static/obs.html`（契约不依赖页面，页面依赖本 API）？

---

## 10. 相关文档

- 前端改造说明：[../ai_observability_frontend.md](../ai_observability_frontend.md)
- 协议摘要同步：[../features/websocket-frontend.md](../features/websocket-frontend.md)

---

*Status: DRAFT — API 设计。Task / 编码需你另行点名。*
