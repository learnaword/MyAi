# 功能：复习计划

<!-- AUTO-GENERATED: feature/review-plan -->

## 做什么

根据评估报告与历史薄弱点，生成个性化复习主题、资料建议与练习方式。

## 入口

| 类型 | 路径 |
|------|------|
| Graph 节点 | `review`（紧接 `evaluate`） |

## 流程

```
读取 EvaluationReport + LongTermMemoryService.topWeakTopics
ReviewPlannerAgent.plan(...)
  → focusTopics[] / resources[] / practiceSuggestions[] / summary
推送 type=review_plan，再推送 type=done
```

## 关键类

| 类 | 职责 |
|----|------|
| `agent/ReviewPlannerAgent` | LLM 生成计划 |
| `model/ReviewPlan` | 计划模型 |

## MVP 范围

- **不调用** GitHub MCP（配置项 `GITHUB_TOKEN` 预留）
- `resources` 由模型给出公开资料/文档名级建议

## 持久化

写入 `interview_sessions.review_plan_json`。

## 相关文档

- [评估报告](./evaluation.md)
- [记忆系统](./memory.md)
