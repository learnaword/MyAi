# AI面试官（InterviewAgent）

面向校招 / 初级 Java 开发者的 AI 模拟面试系统。  
技术栈：Java 20 · Spring Boot · Spring AI Alibaba（DashScope + StateGraph）· MySQL · WebSocket。

## 快速开始

```bash
cp .env.example .env   # 填入 DASHSCOPE_API_KEY
make infra-up
make run
```

浏览器打开：http://localhost:9090/

## 文档

| 文档 | 说明 |
|------|------|
| [docs/README.md](./docs/README.md) | 文档索引 |
| [docs/architecture.md](./docs/architecture.md) | 架构 |
| [docs/tech.md](./docs/tech.md) | 运行与配置 |
| [docs/features/](./docs/features/) | 按功能拆分的技术说明 |
| [AGENTS.md](./AGENTS.md) | Agent / 协作者速查 |

## 项目记忆（Cursor）

- `.cursor/rules/interview-agent-context.mdc` — 架构与目录长期上下文  
- `.cursor/rules/api-change-frontend-docs.mdc` — 接口变更须输出前端文档  
