# AI 全链路可观测 — 前端修改文档

## 涉及页面

- 可选观测台：`static/obs.html`（新建，研发向；**不**改求职者主站首屏）
- 可选脚本：`static/js/obs.js`（新建）
- 主站联调增强（可选）：`static/js/app.js`（仅展示/复制 `traceId`，不强制）
- 主站页面：`static/index.html`（仅当做「复制 traceId」入口时改动）

## API

| 方法 | 路径 / WS type | 说明 |
|------|----------------|------|
| GET | `/api/observability/traces/{traceId}` | 单 trace + spans 时间线 |
| GET | `/api/observability/traces` | trace 列表 |
| GET | `/api/observability/stats/tokens` | Token/成本聚合 |
| GET | `/api/observability/stats/rag` | RAG 效果聚合 |
| GET | `/api/observability/stats/tools` | Skill/Tool 成功率 |
| GET | `/api/observability/stats/agents` | Agent/节点健康度 |
| GET | `/api/observability/status` | 观测模块状态 |
| WS | 出站可选字段 `traceId` | 不新增 type；老客户端可忽略 |

**请求头（REST 均需）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| X-Obs-Admin-Token | String | 是 | 与服务端 `OBS_ADMIN_TOKEN` 一致 |

**列表/统计公共查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| from | String | 是 | ISO-8601 UTC |
| to | String | 是 | ISO-8601 UTC |
| page / size | int | 否 | 仅 traces 列表 |

**响应示例（单 trace）：**

```json
{
  "traceId": "a1b2c3d4e5f6",
  "scene": "INTERVIEW",
  "sessionId": "uuid-interview",
  "status": "OK",
  "startedAt": "2026-07-18T03:00:00.000Z",
  "endedAt": "2026-07-18T03:12:00.000Z",
  "spans": []
}
```

**WS 出站示例：**

```json
{
  "type": "interview_started",
  "content": "面试已开始",
  "sessionId": "uuid-interview",
  "traceId": "a1b2c3d4e5f6"
}
```

完整契约见：`docs/ai_full_link_analysis/api_design_ai_full_link_analysis.md`。

## 前端修改点

### 1. 按钮/弹窗/表格等

可选观测台（独立页，勿塞进主站 hero）：

```html
<!-- static/obs.html -->
<label>Admin Token <input id="obsToken" type="password" autocomplete="off" /></label>
<label>TraceId <input id="traceIdInput" /></label>
<button id="btnLoadTrace" type="button">加载链路</button>
<label>From <input id="fromInput" type="datetime-local" /></label>
<label>To <input id="toInput" type="datetime-local" /></label>
<button id="btnLoadStats" type="button">加载统计</button>
<pre id="traceView"></pre>
<table id="statsTable"></table>
```

主站可选：在系统消息旁显示可复制的 traceId（小字即可）：

```html
<!-- 由 JS 动态插入，不必改静态布局 -->
<span class="trace-id" hidden></span>
```

### 2. data 数据

```js
// static/js/obs.js
let obsToken = sessionStorage.getItem('obsAdminToken') || '';
let currentTraceId = '';

// static/js/app.js（可选）
let lastTraceId = '';
```

### 3. 方法

```js
// static/js/obs.js
async function fetchTrace(traceId) {
  const res = await fetch(`/api/observability/traces/${encodeURIComponent(traceId)}`, {
    headers: { 'X-Obs-Admin-Token': obsToken }
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

async function fetchTokenStats(fromIso, toIso) {
  const q = new URLSearchParams({ from: fromIso, to: toIso });
  const res = await fetch(`/api/observability/stats/tokens?${q}`, {
    headers: { 'X-Obs-Admin-Token': obsToken }
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

// static/js/app.js（可选：收到带 traceId 的消息时）
function onWsMessage(msg) {
  if (msg.traceId) {
    lastTraceId = msg.traceId;
    // 可 console 或提供「复制 TraceId」按钮，不必打扰主流程
  }
}
```

## 注意事项

- 求职者主站 **不要** 默认展示 span 明细或 Token 成本；观测台与主站分离。
- Admin Token 只放在 `sessionStorage` / 本地输入，**禁止**提交进 git。
- `traceId` 为可选字段，旧前端不解析也不应报错。
- `AUTH_ENABLED` 与 `X-Obs-Admin-Token` 是两套机制；观测 API 以 Admin Token 为准。
