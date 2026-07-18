# 需求设计：用户点名制（非流程化）

不做固定流水线。由你**每次指定要做哪一项**；做完停下等你审查或改意见；你再点名下一项（或回头改某项）。

规则：`.cursor/rules/requirements-delivery-pipeline.mdc`

## 需求输入（两种都可以）

| 形态 | 例子 | 你怎么说 |
|------|------|----------|
| **详细文档** | 长文粘贴、已有说明文档 | `做 PRD 分析。功能名 xxx。需求文档如下：……` 或 `基于下面文档做技术方案：……` |
| **一段话 / 一句话** | 「做一个AI全链路分析」 | `做 PRD 分析。做一个AI全链路分析。功能名 ai_full_link_analysis` |

- 详细文档：以你给的内容为准，Agent 做你点名的那一项。  
- 一段话：可先点「做 PRD 分析」展开；也可直接点「做技术方案」等，文档里会标「待确认」。  
- 都要**先点名做哪一项**；未点名会先问你，不会自动开写代码。

## 产出路径

同一功能文档集中在目录 `docs/<name>/`：

```text
docs/<name>/prd_<name>.md
docs/<name>/tech_design_<name>.md
docs/<name>/db_design_<name>.md
docs/<name>/api_design_<name>.md
docs/<name>/tasks_<name>.md
```

`<name>` 为功能短名（小写+下划线），例如 `docs/mock_interview/prd_mock_interview.md`。

## 你可以点的名（任意顺序）

| 你说 | Agent 只做               |
|------|------------------------|
| `做 PRD 分析。需求：……` | `docs/<name>/需求分析.md`  |
| `做技术方案` | `docs/<name>/技术方案.md`  |
| `做数据库设计` | `docs/<name>/数据库设计.md` |
| `做 API 设计` | `docs/<name>/API设计.md` |
| `拆 Task` / `任务拆分` | `docs/<name>/任务拆分.md`  |
| `对齐编码规范` | 规范核对/更新                |
| `更新项目 Context` | AGENTS / rules / 索引    |
| `开始编码` / `实现 xxx` | 写代码（需你明示）              |

## 输出不对时

继续点**同一项**并给改点，例如：

```text
PRD 先不换别的。请修改：……
改完仍只交 PRD，等我再看。
```

## 推荐口令

```text
做 PRD 分析，功能名 mock_interview。需求如下：……
```

```text
这份 PRD 第 2 节改掉 ……（仍做 PRD）
```

```text
做 API 设计（基于 docs/mock_interview/prd_mock_interview.md）
```

```text
开始按 docs/mock_interview/tasks_mock_interview.md 编码
```
