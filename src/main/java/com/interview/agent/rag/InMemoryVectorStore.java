package com.interview.agent.rag;

import com.interview.agent.model.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryVectorStore {

    private final EmbeddingModel embeddingModel;
    private final Map<String, Question> docs = new ConcurrentHashMap<>();
    private final Map<String, float[]> vectors = new ConcurrentHashMap<>();

    public synchronized void upsert(Collection<Question> questions) {
        for (Question q : questions) {
            docs.put(q.getId(), q);
            try {
                float[] vec = embeddingModel.embed(q.getContent());
                vectors.put(q.getId(), vec);
            } catch (Exception e) {
                log.warn("[Vector] embed failed for {}: {}", q.getId(), e.getMessage());
            }
        }
    }

    public synchronized void clear() {
        docs.clear();
        vectors.clear();
    }

    public List<Bm25Index.ScoredQuestion> search(String query, int topK) {
        float[] qv;
        try {
            qv = embeddingModel.embed(query);
        } catch (Exception e) {
            log.warn("[Vector] query embed failed: {}", e.getMessage());
            return List.of();
        }
        List<Bm25Index.ScoredQuestion> scored = new ArrayList<>();
        for (Map.Entry<String, float[]> e : vectors.entrySet()) {
            double sim = cosine(qv, e.getValue());
            if (sim > 0) {
                scored.add(new Bm25Index.ScoredQuestion(docs.get(e.getKey()), sim));
            }
        }
        scored.sort(Comparator.comparingDouble(Bm25Index.ScoredQuestion::score).reversed());
        return scored.stream().limit(topK).toList();
    }

    private double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
