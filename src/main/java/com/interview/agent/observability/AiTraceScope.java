package com.interview.agent.observability;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
public class AiTraceScope {
    private String traceId;
    private String rootSpanId;
    private ObsScene scene;
    private String sessionId;
    private String wsSessionId;
    private Long userId;
    private Instant startedAt;
    @Builder.Default
    private Deque<String> spanStack = new ArrayDeque<>();
    @Builder.Default
    private Map<String, String> attrs = new HashMap<>();
    @Builder.Default
    private AtomicInteger droppedChildHint = new AtomicInteger(0);

    public String currentSpanId() {
        return spanStack.peek();
    }

    public void pushSpan(String spanId) {
        spanStack.push(spanId);
    }

    public void popSpan(String spanId) {
        if (spanId != null && spanId.equals(spanStack.peek())) {
            spanStack.pop();
        }
    }
}
