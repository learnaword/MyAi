# 功能：评估报告

<!-- AUTO-GENERATED: feature/evaluation -->

## 做什么

面试题全部结束后，对整场作答做多维度评分，汇总薄弱点，并对低分题回推参考答案。

## 入口

| 类型 | 路径 |
|------|------|
| Graph 节点 | `evaluate`（题目耗尽或路由到 evaluate） |

## 流程

```
EvaluationAgent.evaluate(turns)
  → overallScore / technicalDepth / clarity / logic / projectDemo
  → summary / suggestions[] / weakTopics[]
LongTermMemoryService.recordWeaknesses(userId, weakTopics)
筛选 score < 60 的题目，附带 referenceAnswer
推送 type=evaluation（含 report + lowScoreReferences）
```

## 关键类

| 类 | 职责 |
|----|------|
| `agent/EvaluationAgent` | LLM 综合评估 |
| `model/EvaluationReport` / `InterviewTurn` | 报告与回合 |
| `memory/LongTermMemoryService` | 薄弱点落库 |

## 输出字段（EvaluationReport）

- `overallScore`、`technicalDepth`、`clarity`、`logic`、`projectDemo`
- `summary`、`suggestions[]`、`weakTopics[]`

## 持久化

写入 `interview_sessions.evaluation_json`（面试线程收尾时）。

## 相关文档

- [复习计划](./review-plan.md)
- [记忆系统](./memory.md)
- [模拟面试](./mock-interview.md)
