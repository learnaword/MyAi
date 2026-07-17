# 功能：模拟面试（提问 / 追问 / 难度）

<!-- AUTO-GENERATED: feature/mock-interview -->

## 做什么

按出题计划逐题提问，等待用户作答；对「部分正确」追问一次；根据作答动态调整难度；支持中途退出。

## 入口

| 类型 | 路径 |
|------|------|
| WS | `start_interview` 启动整图；`answer` 提交作答；`quit` 结束 |
| 编排 | `InterviewOrchestrator` StateGraph |

## Graph 面试环

```
ask_question → grade_answer
                 ├─ followup → ask_followup → next/evaluate
                 ├─ next → ask_question
                 └─ evaluate → …
```

人机等待：`AnswerBridge.prepare` → 推送题目 → `await`；WS `answer` → `submit`。

## 关键类

| 类 | 职责 |
|----|------|
| `graph/InterviewOrchestrator` | StateGraph 编排 |
| `graph/AnswerBridge` | 答题 Future 桥接 |
| `graph/DifficultyController` | 连对 2 升档 / 连错 2 降档 |
| `agent/InterviewerAgent` | 出题文案、判分、追问 |

## 判分约定（GradeResult）

- `verdict`：`good` \| `partial` \| `poor`
- `score`：0–100
- `followUp`：`partial` 时可非空
- `comment`：简评（推送 `type=grade`）

## 动态难度

| 情况 | 行为 |
|------|------|
| 连续 good ×2 | `EASY→MEDIUM→HARD` 升一档，streak 清零 |
| 连续 poor ×2 | 降一档，streak 清零 |
| partial | streak 清零，不升不降 |

## 超时

- 等待作答：`INTERVIEW_ANSWER_TIMEOUT`（默认 300 秒）
- 超时抛错，面试可能失败收尾

## 输出消息类型

`question` / `followup` / `grade` / `answer_received` / `quit`

## 相关文档

- [模拟面试总编排与会话](./session-and-orchestration.md)
- [评估报告](./evaluation.md)
- [WebSocket 与前端](./websocket-frontend.md)
