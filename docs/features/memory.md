# 功能：记忆系统

<!-- AUTO-GENERATED: feature/memory -->

## 做什么

维护聊天短期上下文，以及跨面试的薄弱点长期画像（MySQL）。

## 短期记忆

| 项 | 说明 |
|----|------|
| 类 | `memory/ShortTermMemory` |
| 范围 | 按 WebSocket sessionId |
| 容量 | 最近 20 条消息 |
| 用途 | `ChatAgent` 多轮对话 |
| 持久化 | 否（进程内） |

## 长期记忆

| 项 | 说明 |
|----|------|
| 类 | `memory/LongTermMemoryService` |
| 表 | `weakness_records`（user_id, topic, hit_count, updated_at） |
| 写入 | 评估完成后 `recordWeaknesses` |
| 读取 | 出题规划 / 复习计划 `topWeakTopics` |

## 当前限制

- 面试启动时 `userId` 常为 `null`（未强制登录），薄弱点可能未按用户隔离
- Redis 未接入；长期记忆仅 MySQL 薄弱点表

## 相关文档

- [日常聊天与 Skill](./chat-and-skills.md)
- [出题规划](./question-planning.md)
- [评估报告](./evaluation.md)
- [鉴权](./auth.md)
