# InterviewAgent 文档

<!-- AUTO-GENERATED: index -->

## 项目长期记忆（给 Agent）

| 位置 | 作用 |
|------|------|
| [AGENTS.md](../AGENTS.md) | 仓库级 Agent 速查（目录/主链路/约定） |
| `.cursor/rules/interview-agent-context.mdc` | Cursor **始终应用**的精简架构记忆 |
| `.cursor/rules/api-change-frontend-docs.mdc` | 接口变更时必须输出前端修改文档 |
| `.cursor/rules/requirements-delivery-pipeline.mdc` | 需求设计：**你点名做哪项**，非自动流水 |
| [requirements_pipeline.md](./requirements_pipeline.md) | 点名制说明与口令 |
| 下方 `docs/` | 详细事实来源；改架构时先改这里 |

## 总览

| 文档 | 说明 |
|------|------|
| [architecture.md](./architecture.md) | 系统架构、多 Agent、StateGraph、RAG、数据流 |
| [tech.md](./tech.md) | 启动运行、环境变量、基础设施、排障 |
| [features/](./features/) | **按功能拆分的技术文档**（见下表） |
| [ai_full_link_analysis/](./ai_full_link_analysis/) | AI 全链路可观测（PRD / 技术方案 / 库表 / API） |
| [ai_observability_frontend.md](./ai_observability_frontend.md) | 可观测 REST + 可选 `traceId` 的前端修改说明 |
| [login/](./login/) | 登录 / 注册 / 角色分流（需求分析 · 技术方案 · API） |
| [login_frontend.md](./login_frontend.md) | 登录页、角色门控、WS token、观测 JWT 前端修改说明 |

## 功能文档

| 功能 | 文档 |
|------|------|
| 日常聊天与 Skill | [features/chat-and-skills.md](./features/chat-and-skills.md) |
| 题库上传与 RAG | [features/question-bank-rag.md](./features/question-bank-rag.md) |
| JD 解析 | [features/jd-analysis.md](./features/jd-analysis.md) |
| 简历解析与岗位匹配 | [features/resume-match.md](./features/resume-match.md) |
| 出题规划 | [features/question-planning.md](./features/question-planning.md) |
| 模拟面试（提问/追问/难度） | [features/mock-interview.md](./features/mock-interview.md) |
| 评估报告 | [features/evaluation.md](./features/evaluation.md) |
| 复习计划 | [features/review-plan.md](./features/review-plan.md) |
| 记忆系统 | [features/memory.md](./features/memory.md) |
| 面试会话编排与持久化 | [features/session-and-orchestration.md](./features/session-and-orchestration.md) |
| WebSocket 与前端 | [features/websocket-frontend.md](./features/websocket-frontend.md) |
| 鉴权（JWT，可选） | [features/auth.md](./features/auth.md)（角色分流详见 [login/](./login/)） |

代码/架构变更后请同步更新 `docs/`，并核对 `AGENTS.md` 与 `.cursor/rules/interview-agent-context.mdc`。
