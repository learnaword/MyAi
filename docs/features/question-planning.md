# 功能：出题规划

<!-- AUTO-GENERATED: feature/question-planning -->

## 做什么

基于简历（主）与 JD（辅）、历史薄弱点，规划出题方向；每个方向优先题库原题，否则 LLM 生成。

## 入口

| 类型 | 路径 |
|------|------|
| Graph 节点 | `plan_questions` |
| 配置 | `INTERVIEW_MAX_QUESTIONS`（默认 5） |

## 流程（两阶段）

```
1) LLM 规划 directions：topic / type / difficulty / keywords
2) 对每个方向：
     RagService.retrieveBest(query)
       命中 → source=BANK，原题照搬
       未命中 → LLM 生成，source=GENERATED
推送 type=question_plan
```

题型约定：`基础知识` | `项目经历` | `系统设计`。

## 关键类

| 类 | 职责 |
|----|------|
| `agent/QuestionPlannerAgent` | 方向规划 + 检索/生成 |
| `rag/RagService` | 题库检索 |
| `memory/LongTermMemoryService` | 读取历史薄弱 topic |
| `model/Question` / `Difficulty` / `QuestionSource` | 题目模型 |

## 与动态难度的关系

初始难度默认 `MEDIUM`；面试过程中由 `DifficultyController` 按连对/连错调整（见[模拟面试](./mock-interview.md)）。规划阶段也会带上起始难度。

## 输出

题目列表进入 Graph 状态 `questions`，供面试官节点逐题使用。

## 相关文档

- [题库上传与 RAG](./question-bank-rag.md)
- [模拟面试编排](./mock-interview.md)
- [记忆系统](./memory.md)
