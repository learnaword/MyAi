# AGENTS.md — InterviewAgent

给 AI Agent / 协作者的项目速查。完整说明见 `docs/`。

## 产品

面向校招/初级 Java 开发者的 AI 模拟面试系统：聊天引导 → JD/简历分析 → 题库优先出题 → 多轮面试与追问 → 评估 → 复习计划。

## 栈

Java 20 · Spring Boot 3.4.1 · Spring AI Alibaba 1.1.2.0（DashScope + StateGraph）· MySQL/JPA · WebSocket · 内置静态前端。

## 目录地图

```
com.interview.agent
├── Application.java     # 启动；排除 Redis 自动配置
├── agent/               # 七 Agent
├── graph/               # StateGraph + AnswerBridge + 难度
├── skill/               # Skill（优先于 Chat）
├── rag/                 # 题库检索（内存）
├── loader/              # JD / 简历 / 题库
├── handler/             # WebSocket
├── memory/              # 短期聊天 + 薄弱点落库
├── auth/ config/ model/ repository/ web/
src/main/resources/static/   # 前端
docs/                        # 架构与功能文档（长期事实来源）
.cursor/rules/               # Cursor 始终加载的精简记忆
```

## 主链路

`WebSocket /ws` → chat/Skill 或 `start_interview` → `InterviewOrchestrator`  
Graph：`analyze_jd → match_resume → plan_questions → ask/grade(/followup) → evaluate → review`

## 本地命令

```bash
cp .env.example .env   # 填 DASHSCOPE_API_KEY
make infra-up && make run
# http://localhost:9090/
```

## 文档索引

| 文档 | 用途 |
|------|------|
| `docs/architecture.md` | 架构与数据流 |
| `docs/tech.md` | 运行、环境变量、排障 |
| `docs/features/` | 按功能拆分的技术说明 |
| `.cursor/rules/interview-agent-context.mdc` | Agent 会话长期记忆 |

## 协作约定

1. 架构/包结构/WS 协议变更：先改 `docs/`，再改代码与本文件要点。
2. **接口变更**（新增/改请求/改响应）：必须按 `.cursor/rules/api-change-frontend-docs.mdc` 输出前端修改文档到 `docs/`。
3. 勿提交 `.env`；密钥只用本地环境。
4. 题库在内存，重启后需重新上传（示例 `question.md`）。
