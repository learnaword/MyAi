package com.interview.agent.rag;

import com.interview.agent.model.Question;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class Bm25Index {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final Map<String, Question> docs = new ConcurrentHashMap<>();
    private final Map<String, List<String>> tokensById = new ConcurrentHashMap<>();
    private double avgDl = 1.0;

    public synchronized void upsert(Collection<Question> questions) {
        for (Question q : questions) {
            docs.put(q.getId(), q);
            tokensById.put(q.getId(), tokenize(q.getContent() + " " + nullSafe(q.getTopic())));
        }
        rebuildAvg();
    }

    public synchronized void clear() {
        docs.clear();
        tokensById.clear();
        avgDl = 1.0;
    }

    public int size() {
        return docs.size();
    }

    public List<ScoredQuestion> search(String query, int topK) {
        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty() || docs.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> df = new HashMap<>();
        for (List<String> tokens : tokensById.values()) {
            Set<String> uniq = new HashSet<>(tokens);
            for (String t : qTokens) {
                if (uniq.contains(t)) {
                    df.merge(t, 1, Integer::sum);
                }
            }
        }
        int N = docs.size();
        List<ScoredQuestion> scored = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : tokensById.entrySet()) {
            List<String> docTokens = e.getValue();
            Map<String, Long> tf = docTokens.stream().collect(Collectors.groupingBy(t -> t, Collectors.counting()));
            double score = 0;
            for (String t : qTokens) {
                long f = tf.getOrDefault(t, 0L);
                if (f == 0) continue;
                int dfi = df.getOrDefault(t, 0);
                double idf = Math.log(1 + (N - dfi + 0.5) / (dfi + 0.5));
                double denom = f + K1 * (1 - B + B * docTokens.size() / avgDl);
                score += idf * (f * (K1 + 1)) / denom;
            }
            if (score > 0) {
                scored.add(new ScoredQuestion(docs.get(e.getKey()), score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredQuestion::score).reversed());
        return scored.stream().limit(topK).toList();
    }

    private void rebuildAvg() {
        if (tokensById.isEmpty()) {
            avgDl = 1.0;
            return;
        }
        avgDl = tokensById.values().stream().mapToInt(List::size).average().orElse(1.0);
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fff]+", " ")
                .trim()
                .split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (p.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                    && p.length() > 1) {
                for (int i = 0; i < p.length(); i++) {
                    tokens.add(String.valueOf(p.charAt(i)));
                }
                for (int i = 0; i + 1 < p.length(); i++) {
                    tokens.add(p.substring(i, i + 2));
                }
            } else {
                tokens.add(p);
            }
        }
        return tokens;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public record ScoredQuestion(Question question, double score) {}
}
