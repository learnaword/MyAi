# AI 全链路分析 — PRD

> **需求来源：** 一段话「添加一个AI全链路分析功能」+ 澄清：按 **traceId 串联整条链路**，分别统计 **Token 成本、RAG 效果、Tool 成功率、Agent 执行质量**（AI 平台 / Agent 系统常见可观测性思路）  
> **功能短名：** `ai_full_link_analysis`  
> **状态：** DRAFT — 仅需求；缺信息已标「待确认」或 Assumption  
> **边界：** 本文只写 *what / why*，不写具体埋点 SDK、存储选型、API 字段或任务拆分  
> **方向更正说明：** 初稿误写为「求职者向面试叙事报告」；以本版为准，定位为 **研发/运维向 AI 可观测性**

---

## Problem

InterviewAgent 是多 Agent + StateGraph + RAG +（可选）Tool/Skill 的单体系统。一次用户请求或一场模拟面试会跨过 Chat/Skill、JD/匹配/出题、检索、面试官追问、评估、复习等多个执行单元，并多次调用 LLM。

当前链路对研发几乎**黑盒**：无法用统一 `traceId` 把一次请求下的 LLM 调用、RAG 检索、Tool/Skill 调用、Agent/Graph 节点串起来；也无法分别回答：

- 这场面试 / 这次请求花了多少 Token、钱在哪一段？
- RAG 召回是否有效（命中原题？重排是否有用？空结果率？）
- Tool / Skill 成功还是失败、失败集中在哪？
- 哪个 Agent / Graph 节点慢、易错、质量差？

成本失控、效果回退、线上偶发失败时，只能靠日志肉眼翻找，排查成本高，也难以做持续质量与成本治理。

---

## Evidence

- 产品内观察（可验证）：主链路为 `analyze_jd → match_resume → plan_questions → ask/grade/followup → evaluate → review`，另有 Chat、Skill、RAG；现状无统一 trace 关联与分维度统计能力（代码/文档侧未见 Token/RAG/Tool/Agent 质量的可观测面）。
- 行业实践：多数 AI 平台与 Agent 系统采用「一次请求一个 traceId + span 级事件 + 按维度聚合」做成本与质量观测。
- Assumption — 需通过一次真实排障或成本复盘验证：研发在无 trace 时排查单次面试异常 / 成本异常的平均耗时是否不可接受。

---

## Users

- **Primary**：本系统的 **研发 / 运维 / AI 效果负责人**；触发场景包括：线上异常排查、成本复盘、RAG/Agent 改动后的效果对比、日常质量巡检。
- **Secondary（待确认）**：产品同学是否需要只读聚合看板（无原始 prompt 细节）。
- **Not for**：
  - 校招求职者的「面试表现全链路解读」类产品报告（与本需求无关）
  - 通用 APM 替代（JVM/HTTP 基础设施监控可复用现有手段；本需求聚焦 **AI 语义链路**）

---

## Hypothesis

We believe **为每次用户请求 / 面试会话建立统一 `traceId`，并采集可按 Token / RAG / Tool / Agent 维度查询与聚合的全链路事件** will **把排障与成本/效果治理从「翻日志」变成「按 trace 复盘 + 按指标对比」** for **研发与 AI 效果负责人**.  
We'll know we're right when **一次典型线上问题可用单个 `traceId` 在 ≤N 分钟内还原完整 AI 链路，且可按周输出 Token 成本与 RAG/Tool/Agent 关键指标**（N 与指标口径待确认）。

---

## Success Metrics

| Metric | Target | How measured |
|--------|--------|--------------|
| Trace 覆盖率 | 待确认（建议：WS 主路径与面试 Graph ≥95% 请求有 `traceId` 且可查询） | 有 trace 的请求数 / 总请求数 |
| 单次排障还原时间 | 待确认（建议：典型 AI 异常 ≤10 分钟内还原关键 span） | 排障演练计时 |
| Token 成本可归因率 | 待确认（建议：≥90% LLM 调用可归属到 Agent/节点/场景） | 有归属字段的 LLM span / 全部 LLM span |
| RAG 效果可统计 | 待确认（建议：可按日输出命中率、空结果率、Top-K 分布等约定指标） | 日报/查询是否可用 |
| Tool 成功率可统计 | 待确认（建议：按 Tool/Skill 名称可聚合成功/失败） | 同上 |
| 观测本身开销 | 待确认（建议：P99 延迟增量可接受阈值；不显著拖垮面试链路） | 压测对比 |

---

## Scope

### MVP — 最小可验证假设

建立 **AI 全链路可观测性 MVP**，满足：

1. **Trace 串联**
   - 每次入站请求（至少：WebSocket 消息处理、一场 `start_interview`）分配或传播统一 `traceId`。
   - 链路内关键步骤以 span/事件挂在同一 `traceId` 下（父子关系待确认：是否需要 `spanId` / `parentSpanId`）。
   - 支持：**按 `traceId` 查询整条链路时间线**。

2. **Token 成本**
   - 记录每次 LLM 调用的模型、prompt/completion（或 total）Token、估算成本（计价规则待确认）、归属（Agent / Graph 节点 / 场景：chat | interview | skill | rag-rerank 等）。
   - 支持：按时间范围、场景、Agent/节点 **聚合 Token 与成本**。

3. **RAG 效果**
   - 记录每次检索：query 摘要或 hash、召回条数、是否题库命中、是否走 LLM 兜底、重排是否发生、耗时；可选记录 Top 命中 id（不含或脱敏大段正文，待确认）。
   - 支持：空结果率、命中原题率、兜底率、平均耗时等聚合。

4. **Tool 成功率**
   - 覆盖 Skill / 显式 Tool 调用（范围：现有 SkillRouter 及后续 Tool 待确认）。
   - 记录名称、成功/失败、错误类型、耗时。
   - 支持：按名称聚合成功率与失败原因分布。

5. **Agent 执行质量**
   - 至少记录：Agent 或 Graph 节点名称、起止时间、成功/失败、错误摘要；可选：重试次数、输出是否通过基本校验（校验规则待确认）。
   - 「质量」MVP 默认 = **可观测的执行健康度**（成功率、耗时、失败类型），不强制上「LLM-as-Judge」自动打分（见 Out of scope / Open Questions）。

6. **消费面（待确认形态，MVP 至少一种）**
   - 能查看单 `traceId` 详情；以及 Token / RAG / Tool / Agent 的基础聚合查询或简易后台页。
   - 求职者主流程 UI **不默认暴露**原始 prompt / 内部 span（避免干扰产品体验与泄露）。

### Out of scope（明确不做）

- 求职者向的「面试表现全链路分析报告」
- 完整替换通用基础设施 APM（CPU、JVM、纯 HTTP 延迟大盘等）
- 多租户 SaaS 级商业可观测产品（告警订阅市场、复杂 SLO 编排等）
- MVP 强制引入外部商业 LLM Observability SaaS（是否自建存储待技术方案阶段决定；本 PRD 不绑定）
- 对 prompt/completion **全文长期明文存储**且无脱敏策略（合规风险；存储粒度待确认）
- LLM-as-Judge 自动给每次 Agent 输出打质量分（可作为后续里程碑）
- 自动根据观测结果改写出题/RAG 策略（闭环优化另立需求）

---

## Delivery Milestones

| # | Milestone | Outcome | Status | Plan |
|---|-----------|---------|--------|------|
| 1 | Trace 贯通 + 可查询 | 一次请求/一场面试可用 `traceId` 看到 AI 相关 span 时间线 | pending | — |
| 2 | Token 成本归因 | 可按场景/Agent/节点查看 Token 与估算成本 | pending | — |
| 3 | RAG / Tool / Agent 指标 | 可分别聚合 RAG 效果、Tool 成功率、Agent 执行健康度 | pending | — |
| 4 | 聚合看板或导出 | 按日/周查看趋势；支持抽查坏 trace | pending | — |
| 5 | （可选）质量评分增强 | 抽样 + 人工/Judge 标注，形成质量趋势 | pending | — |

---

## 观测对象与维度（产品层口径）

| 维度 | 要回答的问题 | MVP 最小事件要素（概念） |
|------|--------------|--------------------------|
| Trace | 这次请求整条 AI 链路发生了什么？ | `traceId`、时间线、场景、关联 `sessionId`/`userId`（后两者脱敏策略待确认） |
| Token | 钱和 Token 花在哪？ | 模型、token in/out、成本估算、归属 Agent/节点/场景 |
| RAG | 检索有没有用？ | 召回量、命中/空结果/兜底、重排、耗时 |
| Tool | 工具稳不稳？ | 名称、成功/失败、错误、耗时 |
| Agent | 哪段执行差？ | 节点/Agent 名、耗时、成功/失败、错误摘要 |

**原则：** 同一 `traceId` 串联；四个统计面是同一套事件上的**不同投影**，避免四套互不相干的埋点体系。

---

## Open Questions

- [ ] 观测入口范围：仅面试 Graph，还是同时覆盖 Chat、Skill、题库上传解析、鉴权相关 AI 调用？
- [ ] `traceId` 生命周期：按「单条 WS 消息」、按「一场 interview session」、还是两者嵌套（sessionId + traceId）？
- [ ] 消费界面：仅日志/查询 API，还是需要内置简易 Admin 页？是否鉴权强制开启？
- [ ] Prompt/响应正文：不存 / 存 hash / 存截断 / 全量加密存储？保留多久？
- [ ] Token 计价：固定价目表还是可配置？多模型如何统一？
- [ ] 「Agent 执行质量」MVP 是否包含输出结构校验以外的语义质量信号？
- [ ] Tool 范围是否包含未来 Function Calling；当前 Skill 是否全部纳入？
- [ ] 数据留存与隐私：`userId`、简历/JD 原文是否进入观测存储？
- [ ] 成功率告警是否进 MVP，还是仅查询与聚合？

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 埋点侵入过重，拖慢主链路或代码耦合失控 | 中 | 高 | MVP 限定关键切面；异步落库；技术方案阶段约束侵入面 |
| 存储 prompt/简历导致隐私与合规风险 | 中 | 高 | 默认脱敏/不存原文；明确保留期；Admin 鉴权 |
| 指标口径不清，团队各说各话 | 高 | 中 | PRD/后续设计冻结「命中率」「成功率」「归属」定义 |
| 与通用 APM 重复建设 | 低 | 中 | 本需求只覆盖 AI 语义维度；基础设施指标不重复造轮子 |
| 覆盖不全导致「有 trace 仍缺关键 span」不可信 | 中 | 高 | 以 Trace 覆盖率与排障演练作为发布门槛 |

---

*Status: DRAFT — requirements only. 技术方案 / 库表 / API / Task / 编码需你另行点名。*
