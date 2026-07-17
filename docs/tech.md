# InterviewAgent 技术文档

<!-- AUTO-GENERATED: tech -->

## Overview

本文档覆盖**运行、配置、基础设施与排障**。  
各业务能力的详细说明已拆到 [`features/`](./features/)（聊天、题库、JD、匹配、出题、面试、评估、复习、记忆、鉴权等）。

配套：[architecture.md](./architecture.md) · [功能索引](./features/README.md)

## Feature Docs

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
| 鉴权（JWT，可选） | [features/auth.md](./features/auth.md) |

## Prerequisites

- JDK 20+
- Maven 3.9+
- Docker / Docker Compose（至少需要 MySQL）
- 有效的 [阿里云百炼](https://bailian.console.aliyun.com/) `DASHSCOPE_API_KEY`

## Quick Start

```bash
cp .env.example .env
# 编辑 .env，填入真实 DASHSCOPE_API_KEY

make infra-up
make run
```

浏览器：`http://localhost:9090/` · 健康检查：`GET /health`

## Makefile Commands

| Command | Description |
|---------|-------------|
| `make run` | 启动 Spring Boot |
| `make build` | `mvn compile` |
| `make package` | 打包（跳过测试） |
| `make test` | 运行测试 |
| `make infra-up` / `infra-down` / `infra-status` | 基础设施 |
| `make docker-build` / `docker-run` | 应用镜像 |
| `make clean` | 清理 |

## Environment Variables

来源：`.env.example`（`spring-dotenv` 加载项目根 `.env`）。不要用 shell `source .env`。

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DASHSCOPE_API_KEY` | **Yes** | — | 百炼 API Key |
| `LLM_MODEL` | No | `qwen-plus` | Chat 模型 |
| `EMBEDDING_MODEL` | No | `text-embedding-v3` | Embedding |
| `DASHSCOPE_READ_TIMEOUT` | No | `300` | Jetty 总超时（秒） |
| `SERVER_PORT` | No | `9090` | 端口 |
| `MYSQL_URL` / `USERNAME` / `PASSWORD` | No | 本地默认 | MySQL |
| `AUTH_ENABLED` | No | `false` | 是否强制 JWT |
| `JWT_SECRET` | 鉴权开启时建议改 | 内置默认 | JWT 密钥 |
| `INTERVIEW_MAX_QUESTIONS` | No | `5` | 题量 |
| `INTERVIEW_ANSWER_TIMEOUT` | No | `300` | 作答等待秒数 |
| `MILVUS_ENABLED` | No | `false` | 是否用 Milvus |
| `GITHUB_TOKEN` | No | 空 | 预留 |

## Infrastructure

| Service | Port | MVP 必需 |
|---------|------|----------|
| mysql | 3306 | 是（库 `interview_agent`） |
| redis / milvus | 6379 / 19530 | 否 |

表由 JPA `ddl-auto=update` 自动创建。详见各功能文档中的持久化说明。

## HTTP / WS（摘要）

| 通道 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `POST /api/register` `POST /api/login` | 见 [auth](./features/auth.md) |
| `WS /ws` | 见 [websocket-frontend](./features/websocket-frontend.md) |

## Troubleshooting

| 现象 | 处理 |
|------|------|
| `${MYSQL_URL}` 未解析 | 配置 `.env`，从项目根启动 |
| InvalidApiKey / ChatCompletion 反序列化 | 更新 API Key 后重启 |
| Total timeout 10000 ms | 确认超时配置；调大 `DASHSCOPE_READ_TIMEOUT` |
| 题库上传走 LLM 失败 | 用题/答或 JSON，见 [question-bank-rag](./features/question-bank-rag.md) |
| 重启后无题 | 题库在内存，需重新上传 |

## Related Paths

`application.yml` · `.env.example` · `docker-compose.yml` · `docs/features/`
