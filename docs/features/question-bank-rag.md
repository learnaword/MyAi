# 功能：题库上传与 RAG 检索

<!-- AUTO-GENERATED: feature/question-bank-rag -->

## 做什么

用户上传面试题库；出题时优先从题库检索原题（不改写），未命中再 LLM 生成。

## 入口

| 类型 | 路径 |
|------|------|
| WS | `type=upload_questions`：`filename` + `fileBase64` |
| 出题消费 | `QuestionPlannerAgent` → `RagService.retrieveBest` |

## 流程

```
base64 解码 → PDF/DOCX/文本抽取
  → 本地解析（题/答、编号题、JSON）
  → 失败才 LLM 解析
  → RagService.upsertBank
       ├─ Bm25Index
       └─ InMemoryVectorStore（Embedding）
```

检索：BM25 TopK + 向量 TopK → ID 去重 → `LlmReranker` → 取最优一道。

## 关键类

| 类 | 职责 |
|----|------|
| `loader/QuestionBankLoader` | 解析题库文件 |
| `rag/RagService` | 入库与检索门面 |
| `rag/Bm25Index` | 关键词召回 |
| `rag/InMemoryVectorStore` | Embedding 向量召回 |
| `rag/LlmReranker` | LLM 重排 |

## 推荐题库格式

```text
题：……
答：……
```

或：

```json
[{"content":"题目","referenceAnswer":"答案","topic":"JVM","difficulty":"MEDIUM"}]
```

示例文件：仓库根目录 `question.md`。

## 配置

| 配置 | 说明 |
|------|------|
| `app.rag.top-k` | 召回条数（默认 8） |
| `app.rag.rerank-top-n` | 重排保留数（默认 3） |
| `EMBEDDING_MODEL` | 默认 `text-embedding-v3` |
| `MILVUS_ENABLED` | 默认 `false`（MVP 不用 Milvus） |

## 输出

- 上传成功：`type=upload_result`，含题目数量
- 题目 `source=BANK` 时内容与参考答案保持原文

## 注意

- 题库在 **JVM 内存**，进程重启需重新上传
- WebSocket 单条消息缓冲约 32MB，支持较大文件

## 相关文档

- [出题规划](./question-planning.md)
- [WebSocket 与前端](./websocket-frontend.md)
