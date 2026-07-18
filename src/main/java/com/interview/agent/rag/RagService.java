package com.interview.agent.rag;

import com.interview.agent.config.AppConfig;
import com.interview.agent.model.Question;
import com.interview.agent.observability.AiTraceContext;
import com.interview.agent.observability.SpanType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RagService {

    private final Bm25Index bm25Index;
    private final InMemoryVectorStore vectorStore;
    private final LlmReranker reranker;
    private final AppConfig appConfig;
    private final AiTraceContext traceContext;

    public synchronized void upsertBank(List<Question> questions) {
        bm25Index.upsert(questions);
        vectorStore.upsert(questions);
    }

    public synchronized void clearBank() {
        bm25Index.clear();
        vectorStore.clear();
    }

    public int bankSize() {
        return bm25Index.size();
    }

    public Optional<Question> retrieveBest(String directionQuery) {
        try (AiTraceContext.ActiveSpan span = traceContext.startSpan(SpanType.RAG, "rag.retrieve")) {
            try {
                int topK = appConfig.getRag().getTopK();
                int topN = appConfig.getRag().getRerankTopN();

                Map<String, Question> merged = new LinkedHashMap<>();
                for (Bm25Index.ScoredQuestion s : bm25Index.search(directionQuery, topK)) {
                    merged.put(s.question().getId(), s.question());
                }
                for (Bm25Index.ScoredQuestion s : vectorStore.search(directionQuery, topK)) {
                    merged.putIfAbsent(s.question().getId(), s.question());
                }
                span.draft().setRagCandidateCount(merged.size());
                span.draft().getAttributes().put("bankSize", bankSize());
                if (merged.isEmpty()) {
                    span.draft().setRagEmpty(true);
                    span.draft().setRagHit(false);
                    span.draft().setRagReranked(false);
                    span.ok();
                    return Optional.empty();
                }
                LlmReranker.RerankOutcome outcome =
                        reranker.rerank(directionQuery, new ArrayList<>(merged.values()), topN);
                span.draft().setRagEmpty(false);
                span.draft().setRagHit(true);
                span.draft().setRagReranked(outcome.attempted());
                span.draft().setRagRerankFallback(outcome.fallback());
                List<String> topIds = outcome.questions().stream().map(Question::getId).limit(5).toList();
                span.draft().getAttributes().put("topQuestionIds", topIds);
                span.ok();
                return outcome.questions().stream().findFirst();
            } catch (RuntimeException e) {
                span.error(e);
                throw e;
            }
        }
    }
}
