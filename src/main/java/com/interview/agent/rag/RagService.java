package com.interview.agent.rag;

import com.interview.agent.config.AppConfig;
import com.interview.agent.model.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RagService {

    private final Bm25Index bm25Index;
    private final InMemoryVectorStore vectorStore;
    private final LlmReranker reranker;
    private final AppConfig appConfig;

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
        int topK = appConfig.getRag().getTopK();
        int topN = appConfig.getRag().getRerankTopN();

        Map<String, Question> merged = new LinkedHashMap<>();
        for (Bm25Index.ScoredQuestion s : bm25Index.search(directionQuery, topK)) {
            merged.put(s.question().getId(), s.question());
        }
        for (Bm25Index.ScoredQuestion s : vectorStore.search(directionQuery, topK)) {
            merged.putIfAbsent(s.question().getId(), s.question());
        }
        if (merged.isEmpty()) {
            return Optional.empty();
        }
        List<Question> reranked = reranker.rerank(directionQuery, new ArrayList<>(merged.values()), topN);
        return reranked.stream().findFirst();
    }
}
