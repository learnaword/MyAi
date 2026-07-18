package com.interview.agent.auth;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 10 * 60 * 1000L;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public void check(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            if (q.size() >= MAX_ATTEMPTS) {
                throw AuthException.tooMany("too many login attempts, try later");
            }
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> q = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
                q.pollFirst();
            }
            q.addLast(now);
        }
    }

    public void clear(String key) {
        attempts.remove(key);
    }
}
