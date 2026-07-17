package com.interview.agent.graph;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AnswerBridge {

    private final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public void prepare(String sessionId) {
        pending.put(sessionId, new CompletableFuture<>());
    }

    public void submit(String sessionId, String answer) {
        CompletableFuture<String> f = pending.get(sessionId);
        if (f != null) {
            f.complete(answer);
        }
    }

    public void cancel(String sessionId) {
        CompletableFuture<String> f = pending.remove(sessionId);
        if (f != null) {
            f.completeExceptionally(new InterruptedException("quit"));
        }
    }

    public String await(String sessionId, int timeoutSeconds) throws Exception {
        CompletableFuture<String> f = pending.computeIfAbsent(sessionId, k -> new CompletableFuture<>());
        try {
            return f.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("等待回答超时");
        } finally {
            pending.remove(sessionId, f);
        }
    }
}
