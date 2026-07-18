# AI 面试官（InterviewAgent）

面向校招 / 初级 Java 开发者的 **AI 模拟面试系统**。  
用户通过内置 Web 页面完成日常答疑、上传题库、提交 JD 与简历，系统以多 Agent + StateGraph 编排完整面试链路：岗位分析 → 简历匹配 → 题库优先出题 → 多轮提问与追问 → 评估报告 → 复习计划。

主交互为 WebSocket（`/ws`），后端为单一 Spring Boot 应用，默认端口 **9090**。

---

## 能做什么

| 能力 | 说明 |
|------|------|
| 日常聊天 | 答疑与引导；未命中 Skill 时走 `ChatAgent` |
| 快速测验 / 知识讲解 | 可插拔 Skill，优先于普通聊天 |
| 题库上传与 RAG | 上传题库后内存检索；命中原题不改写 |
| JD 解析 | 将岗位描述结构化为技能与考察方向 |
| 简历匹配 | 对比 JD 与简历，输出匹配度、强项与短板 |
| 模拟面试 | 多轮提问、判分、追问、动态难度 |
| 评估报告 | 多维评估，薄弱点落库 |
| 复习计划 | 基于评估结果生成复习建议 |
| 可选鉴权 | JWT 登录 / 注册（`AUTH_ENABLED` 控制） |

---

## 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 语言 | Java 20 | Maven 构建 |
| 框架 | Spring Boot 3.4.1 | 单体应用 |
| AI | Spring AI Alibaba 1.1.2.0 | DashScope + Agent Framework |
| 编排 | StateGraph | 面试阶段与条件边（追问 / 下一题 / 评估） |
| LLM | 通义千问 | 默认 `qwen-plus` / `text-embedding-v3` |
| 持久化 | MySQL 8 + JPA | 用户、会话、薄弱点；`ddl-auto: update` |
| 检索 | 内存 BM25 + Embedding + LLM 重排 | 默认不用 Milvus |
| 交互 | WebSocket + 静态前端 | `src/main/resources/static/` |
| 基础设施 | Docker Compose | MySQL 必需；Redis / Milvus 可选 |

---

## 主流程

```text
浏览器 (static/)
    │  WebSocket /ws
    ▼
WebSocketHandler
    ├─ chat / Skill（快速测验、知识讲解）
    └─ start_interview → InterviewOrchestrator (StateGraph)
           analyze_jd → match_resume → plan_questions
                → ask / grade (/ followup) → evaluate → review
```

面试图节点职责概览：

| 节点 | 职责 |
|------|------|
| `analyze_jd` | 解析 JD |
| `match_resume` | 简历与岗位匹配 |
| `plan_questions` | 规划方向；题库优先，未命中再 LLM 生成 |
| `ask_question` / `ask_followup` | 出题；通过 `AnswerBridge` 等待用户作答 |
| `grade_answer` | 评分；可追问一次；动态调整难度 |
| `evaluate` | 终评并写入薄弱点 |
| `review` | 生成复习计划 |

---

## 项目结构

```text
com.interview.agent
├── Application.java     # 启动入口（排除 Redis 自动配置）
├── agent/               # Chat + JD / 匹配 / 出题 / 面试官 / 评估 / 复习
├── graph/               # StateGraph、AnswerBridge、难度控制
├── skill/               # 快速测验、知识讲解（优先于 Chat）
├── rag/                 # 题库检索（内存）
├── loader/              # JD / 简历 / 题库解析
├── handler/             # WebSocket 协议
├── memory/              # 短期聊天 + 长期薄弱点
├── auth/ config/ model/ repository/ web/
src/main/resources/static/   # 内置前端
docs/                        # 架构与功能文档（事实来源）
```

开发时常用入口：

| 想改什么 | 从这里看 |
|----------|----------|
| WS 协议 | `handler/WebSocketHandler.java` |
| 面试编排 | `graph/InterviewOrchestrator.java` |
| 检索策略 | `rag/RagService.java` |
| 前端交互 | `src/main/resources/static/js/app.js` |
| 配置 | `application.yml` + `.env.example` |

---

## 快速开始

### 环境要求

- JDK 20+
- Maven 3.9+
- Docker / Docker Compose（至少启动 MySQL）
- [阿里云百炼](https://bailian.console.aliyun.com/) `DASHSCOPE_API_KEY`

### 启动

```bash
cp .env.example .env
# 编辑 .env，填入真实 DASHSCOPE_API_KEY

make infra-up   # 启动 MySQL 等基础设施
make run        # 启动 Spring Boot
```

浏览器打开：http://localhost:9090/  
健康检查：`GET /health`

> `.env` 由应用内 `spring-dotenv` 加载，不要用 `source .env`（URL 中的 `&` 会被 shell 误解析）。

### 常用命令

| 命令 | 说明 |
|------|------|
| `make run` | 启动应用 |
| `make build` | `mvn compile` |
| `make test` | 运行测试 |
| `make package` | 打包（跳过测试） |
| `make infra-up` / `infra-down` / `infra-status` | 基础设施 |
| `make docker-build` / `docker-run` | 应用镜像 |
| `make clean` | 清理构建产物 |

---

## 配置说明（摘要）

完整变量见 [docs/tech.md](./docs/tech.md) 与 [.env.example](./.env.example)。

| 变量 | 必填 | 默认 | 说明 |
|------|------|------|------|
| `DASHSCOPE_API_KEY` | 是 | — | 百炼 API Key |
| `LLM_MODEL` | 否 | `qwen-plus` | Chat 模型 |
| `EMBEDDING_MODEL` | 否 | `text-embedding-v3` | Embedding |
| `SERVER_PORT` | 否 | `9090` | HTTP / WS 端口 |
| `MYSQL_*` | 否 | 本地默认 | MySQL 连接 |
| `AUTH_ENABLED` | 否 | 见 `.env.example` | 是否强制 JWT |
| `INTERVIEW_MAX_QUESTIONS` | 否 | `5` | 单场题量 |
| `INTERVIEW_ANSWER_TIMEOUT` | 否 | `300` | 作答等待（秒） |
| `MILVUS_ENABLED` | 否 | `false` | 是否启用外部向量库 |
| `DASHSCOPE_READ_TIMEOUT` | 否 | `300` | LLM 请求总超时（秒） |

---

## 接口与交互（摘要）

| 通道 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `POST /api/register` · `POST /api/login` | 鉴权开启时可用 |
| `WS /ws` | 主协议：`chat`、`upload_questions`、`start_interview`、`answer` 等 |

协议细节见 [docs/features/websocket-frontend.md](./docs/features/websocket-frontend.md)。

---

## 设计要点

- **多 Agent 分工**：JD / 匹配 / 出题 / 面试 / 评估 / 复习各司其职，避免单 Prompt 角色过载。
- **题库优先**：RAG 命中则原题照搬（不改写参考答案）；未命中再 LLM 生成。
- **内存题库**：默认不依赖 Milvus；**进程重启后题库丢失**，需重新上传（可用仓库内 `question.md` 示例）。
- **人机协作**：出题节点通过 `AnswerBridge` 挂起，用户经 WebSocket 提交 `answer` 后继续。
- **鉴权可选**：本地联调可按需关闭 `AUTH_ENABLED`；生产请配置可靠 `JWT_SECRET`。

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [docs/README.md](./docs/README.md) | 文档总索引 |
| [docs/architecture.md](./docs/architecture.md) | 架构、数据流、Agent 与 RAG |
| [docs/tech.md](./docs/tech.md) | 运行、环境变量、排障 |
| [docs/features/](./docs/features/) | 按功能拆分的技术说明 |
| [AGENTS.md](./AGENTS.md) | Agent / 协作者速查 |

功能文档推荐阅读顺序：  
`websocket-frontend` → `session-and-orchestration` → `jd-analysis` → `resume-match` → `question-bank-rag` → `question-planning` → `mock-interview` → `evaluation` → `review-plan`。

---

## 协作约定（摘要）

1. 架构 / 包结构 / WS 协议变更：先改 `docs/`，再改代码。
2. 新增或修改对外接口：按 `.cursor/rules/api-change-frontend-docs.mdc` 输出前端修改文档到 `docs/`。
3. 勿提交 `.env`；密钥仅放本地环境。
4. 需求设计采用「点名制」：指定做 PRD / 技术方案 / API / Task 等其中一项，详见 [docs/requirements_pipeline.md](./docs/requirements_pipeline.md)。

---

## 已知限制

- 题库在 JVM 内存中，重启需重新上传。
- Redis / Milvus 在 Compose 中已备，应用侧默认未用或关闭。
- GitHub MCP 复习资源检索为预留能力，MVP 未接入。
- 更多缺口与路线图见 [docs/architecture.md](./docs/architecture.md) 末尾。
