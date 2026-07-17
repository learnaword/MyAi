# 功能：简历解析与岗位匹配

<!-- AUTO-GENERATED: feature/resume-match -->

## 做什么

解析候选人简历（文本或 PDF/DOCX），结合 JD 分析结果输出匹配度、强项与短板。

## 入口

| 类型 | 路径 |
|------|------|
| WS | `start_interview`：`resumeText` 或 `resumeBase64` + `resumeFilename` |
| Graph 节点 | `match_resume` |

## 流程

```
有 resumeText → 使用文本
有 resumeBase64 → ResumeLoader（PDFBox / POI）
ResumeMatchAgent.match(jd, resume)
  → score / strengths[] / gaps[] / summary
推送 type=match_report
写入 interview_sessions.match_report_json（面试结束路径一并持久化）
```

## 关键类

| 类 | 职责 |
|----|------|
| `loader/ResumeLoader` | PDF/DOCX/纯文本 |
| `agent/ResumeMatchAgent` | LLM 匹配评估 |
| `model/MatchReport` | 报告模型 |

## 输出字段（MatchReport）

- `score`：0–100
- `strengths[]`：强项
- `gaps[]`：短板（影响后续出题侧重）
- `summary`：综述

## 依赖

- DashScope（匹配调用耗时可能较长，依赖 `DASHSCOPE_READ_TIMEOUT`）
- 支持扩展名：`.pdf` / `.docx` / 其他按 UTF-8 文本

## 相关文档

- [JD 解析](./jd-analysis.md)
- [出题规划](./question-planning.md)
