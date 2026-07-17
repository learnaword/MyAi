# 功能：日常聊天与 Skill

<!-- AUTO-GENERATED: feature/chat-and-skills -->

## 做什么

用户通过 WebSocket 闲聊、咨询面试技巧；命中 Skill 时走专业多轮能力（快速测验、知识讲解），否则走通用 `ChatAgent`。

## 入口

| 类型 | 路径 |
|------|------|
| WS | `type=chat`，字段 `content` |
| 前端 | 对话区发送（非面试作答态） |

## 处理优先级

```
active Skill 会话 → Skill 关键词匹配 → ChatAgent
```

实现：`SkillRouter.route(sessionKey, userInput)`。

## 关键类

| 类 | 职责 |
|----|------|
| `skill/SkillRouter` | 路由与会话保持 |
| `skill/QuickQuizSkill` | 快速测验（出题 → 点评） |
| `skill/KnowledgeExplainSkill` | 知识讲解 |
| `agent/ChatAgent` | 通用对话；短期历史最多 20 条 |
| `memory/ShortTermMemory` | 按 WS session 存聊天消息 |

## Skill 触发词（示例）

- 快速测验：`快速测验` / `小测` / `quiz`
- 知识讲解：`讲解` / `解释一下` / `什么是` / `原理`
- 退出测验：回复含 `退出技能`

## 输出

服务端回推 `type=chat`（或 Skill 过程中的说明文案）。

## 依赖

- DashScope `ChatModel`
- 不落库（仅内存短期历史）

## 相关文档

- [WebSocket 与前端](./websocket-frontend.md)
- [记忆系统](./memory.md)
