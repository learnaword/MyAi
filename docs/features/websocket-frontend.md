# 功能：WebSocket 与前端

<!-- AUTO-GENERATED: feature/websocket-frontend -->

## 做什么

提供浏览器 UI 与后端实时通道，承载聊天、题库上传、面试全流程。

## 入口

| 类型 | 路径 |
|------|------|
| 页面 | `http://host:9090/` → `static/index.html`（USER；未登录见登录页） |
| 登录/注册 | `login.html` / `register.html`（见 [login API](../login/API设计.md)） |
| 观测 | `obs.html`（ADMIN） |
| WS | `ws://host:9090/ws?token=<jwt>`（`AUTH_ENABLED=true` 时必填；仅 **USER**） |
| 配置 | `config/WebSocketConfig`（缓冲 32MB，空闲 10 分钟） |

身份：握手解析 JWT，`userId` 写入会话；客户端不可伪造。缺/无效 token → 前端提示「用户未登录」；ADMIN 连 WS → 拒绝。

## 前端文件

| 文件 | 职责 |
|------|------|
| `static/index.html` | 布局：JD/简历/题库 + 对话区 |
| `static/js/app.js` | 连接 WS、消息收发、作答态切换 |
| `static/css/app.css` | 样式 |

## 客户端消息类型

| type | 用途 |
|------|------|
| `chat` | 闲聊 / Skill |
| `start_interview` | 开始面试 |
| `answer` | 提交答案 |
| `upload_questions` | 上传题库 |
| `quit` | 结束面试 |

## 服务端常见推送

`system` / `chat` / `error` / `interview_started` / `phase` / `jd_analysis` / `match_report` / `question_plan` / `question` / `followup` / `grade` / `evaluation` / `review_plan` / `done` / `upload_result`

### 可选出站字段（AI 可观测）

当 `app.observability.enabled=true` 时，部分出站 JSON 可带 `traceId`（不新增 type，老客户端可忽略）。详见 [AI 全链路 API](../ai_full_link_analysis/api_design_ai_full_link_analysis.md) 与 [前端修改文档](../ai_observability_frontend.md)。

## 关键类

| 类 | 职责 |
|----|------|
| `handler/WebSocketHandler` | 协议路由与异步面试线程 |
| `model/WsInboundMessage` / `WsOutboundMessage` | 消息模型 |

## 前端作答态

收到 `question` 或 `followup` 后，输入框下一条按 `answer` 发送；`done`/`quit`/`evaluation` 后回到聊天态。

## 相关文档

- [日常聊天与 Skill](./chat-and-skills.md)
- [模拟面试](./mock-interview.md)
- [题库上传与 RAG](./question-bank-rag.md)
