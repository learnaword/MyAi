# 功能：面试会话编排与持久化

<!-- AUTO-GENERATED: feature/session-and-orchestration -->

## 做什么

用 StateGraph 把 JD→匹配→出题→面试→评估→复习串成一条流水线，并将会话摘要写入 MySQL。

## 入口

| 类型 | 路径 |
|------|------|
| WS | `start_interview` |
| 核心类 | `graph/InterviewOrchestrator` |
| 落库 | `InterviewSessionRepository` |

## Graph 全链路

```
START → analyze_jd → match_resume → plan_questions
     → ask_question ⇄ grade_answer ⇄ ask_followup
     → evaluate → review → END
```

详见 [architecture.md](../architecture.md) 中的流程图。

## 会话持久化（interview_sessions）

| 时机 | 动作 |
|------|------|
| 开始 | 写入 id、jd_text、resume_text、status=RUNNING |
| 成功结束 | status=FINISHED，写入 match/evaluation/review JSON，finished_at |
| 异常 | status=FAILED |

字段详见 [tech.md](../tech.md) 数据库章节。

## 并发模型

- 每个面试在 `interviewExecutor` 线程池异步执行 `orchestrator.run`
- 同一 WS 连接同时仅允许一场进行中面试
- 断线或 `quit` → `requestQuit` + 取消 `AnswerBridge`

## 事件推送

节点通过 `InterviewEventSink` 回调 `WebSocketHandler.send`，把阶段结果实时推到前端。

## 相关配置

| 变量 | 含义 |
|------|------|
| `INTERVIEW_MAX_QUESTIONS` | 题量 |
| `INTERVIEW_ANSWER_TIMEOUT` | 单题等待秒数 |

## 相关文档

- [JD 解析](./jd-analysis.md)
- [简历匹配](./resume-match.md)
- [出题规划](./question-planning.md)
- [模拟面试](./mock-interview.md)
- [评估报告](./evaluation.md)
- [复习计划](./review-plan.md)
