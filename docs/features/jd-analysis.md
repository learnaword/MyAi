# 功能：JD 解析

<!-- AUTO-GENERATED: feature/jd-analysis -->

## 做什么

将岗位 JD（粘贴文本或招聘页 URL）解析为结构化需求，供简历匹配与出题使用。

## 入口

| 类型 | 路径 |
|------|------|
| WS | `start_interview` 的 `jd` 或 `jdUrl` |
| Graph 节点 | `analyze_jd` |

## 流程

```
JdLoader.load(jdText, jdUrl)
  ├─ 有文本 → 直接使用
  └─ 仅 URL → HTTP 抓取 → 去 script/style → 剥 HTML → 截断过长文本
JdAnalysisAgent.analyze(jdText)
  → JSON：title / techStack / requiredSkills / experience / summary
推送 type=jd_analysis
```

## 关键类

| 类 | 职责 |
|----|------|
| `loader/JdLoader` | 文本或 URL 抓取 |
| `agent/JdAnalysisAgent` | LLM 结构化 |
| `model/JdRequirement` | 结果模型 |
| `graph/InterviewOrchestrator#analyzeJd` | 编排节点 |

## 输出字段（JdRequirement）

- `title`：岗位名
- `techStack[]`：技术栈
- `requiredSkills[]`：能力要求
- `experience`：经验要求
- `summary`：摘要

## 依赖与限制

- URL 抓取依赖目标站可访问；反爬/登录墙可能导致失败，可降级为粘贴文本
- 需要有效 DashScope Key

## 相关文档

- [简历匹配](./resume-match.md)
- [模拟面试编排](./mock-interview.md)
