package com.interview.agent.observability;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class AiTraceContext {

    private static final ThreadLocal<AiTraceScope> LOCAL = new ThreadLocal<>();
    private static final ThreadLocal<ActiveSpan> CURRENT_AGENT_SPAN = new ThreadLocal<>();
    private static final Map<String, AiTraceScope> BY_SESSION = new ConcurrentHashMap<>();

    private final AiSpanRecorder recorder;

    public static AiTraceScope current() {
        return LOCAL.get();
    }

    public static String currentTraceId() {
        AiTraceScope scope = LOCAL.get();
        return scope == null ? null : scope.getTraceId();
    }

    public static ActiveSpan currentAgentSpan() {
        return CURRENT_AGENT_SPAN.get();
    }

    public boolean enabled() {
        return recorder.enabled();
    }

    public AiTraceScope openRootNamed(ObsScene scene, String rootName, String wsSessionId, String sessionId, Long userId) {
        if (!recorder.enabled()) {
            return null;
        }
        String traceId = AiSpanRecorder.newId();
        String rootSpanId = AiSpanRecorder.newId();
        Instant now = Instant.now();
        AiTraceScope scope = AiTraceScope.builder()
                .traceId(traceId)
                .rootSpanId(rootSpanId)
                .scene(scene)
                .sessionId(sessionId)
                .wsSessionId(wsSessionId)
                .userId(userId)
                .startedAt(now)
                .build();
        scope.getAttrs().put("rootName", "root." + (rootName == null ? "unknown" : rootName));
        scope.pushSpan(rootSpanId);
        bind(scope);
        recorder.persistTraceStart(scope);
        if (sessionId != null && !sessionId.isBlank()) {
            BY_SESSION.put(sessionId, scope);
        }
        return scope;
    }

    public void closeRoot(SpanStatus status, String errorSummary) {
        AiTraceScope scope = LOCAL.get();
        if (scope == null) {
            return;
        }
        Instant end = Instant.now();
        recorder.record(AiSpanDraft.builder()
                .spanId(scope.getRootSpanId())
                .traceId(scope.getTraceId())
                .parentSpanId(null)
                .spanType(SpanType.ROOT)
                .name(scope.getAttrs().getOrDefault("rootName", "root"))
                .status(status == null ? SpanStatus.OK : status)
                .startedAt(scope.getStartedAt())
                .endedAt(end)
                .errorType(status == SpanStatus.ERROR ? "ROOT_ERROR" : null)
                .errorMessage(errorSummary)
                .build());
        String traceStatus = status == SpanStatus.ERROR ? "ERROR" : "OK";
        if (errorSummary != null && errorSummary.startsWith("CANCELLED")) {
            traceStatus = "CANCELLED";
        }
        recorder.persistTraceEnd(scope, traceStatus, errorSummary);
        if (scope.getSessionId() != null) {
            BY_SESSION.remove(scope.getSessionId(), scope);
        }
        clear();
    }

    public void bind(AiTraceScope scope) {
        LOCAL.set(scope);
        if (scope != null) {
            MDC.put("traceId", scope.getTraceId());
        } else {
            MDC.remove("traceId");
        }
    }

    public void clear() {
        LOCAL.remove();
        MDC.remove("traceId");
    }

    public AiTraceScope bindSession(String sessionId) {
        AiTraceScope scope = BY_SESSION.get(sessionId);
        if (scope != null) {
            bind(scope);
        }
        return scope;
    }

    public void unregisterSession(String sessionId) {
        if (sessionId != null) {
            BY_SESSION.remove(sessionId);
        }
    }

    public AiTraceScope scopeForSession(String sessionId) {
        return sessionId == null ? null : BY_SESSION.get(sessionId);
    }

    public void setAgentNode(String agent, String node) {
        AiTraceScope scope = LOCAL.get();
        if (scope == null) {
            return;
        }
        if (agent != null) {
            scope.getAttrs().put("agent", agent);
        } else {
            scope.getAttrs().remove("agent");
        }
        if (node != null) {
            scope.getAttrs().put("node", node);
        } else {
            scope.getAttrs().remove("node");
        }
    }

    public String agent() {
        AiTraceScope scope = LOCAL.get();
        return scope == null ? null : scope.getAttrs().get("agent");
    }

    public String node() {
        AiTraceScope scope = LOCAL.get();
        return scope == null ? null : scope.getAttrs().get("node");
    }

    public <T> T runWithSession(String sessionId, Supplier<T> action) {
        AiTraceScope previous = LOCAL.get();
        try {
            bindSession(sessionId);
            return action.get();
        } finally {
            if (previous != null) {
                bind(previous);
            } else {
                clear();
            }
        }
    }

    public ActiveSpan startSpan(SpanType type, String name) {
        AiTraceScope scope = LOCAL.get();
        if (scope == null || !recorder.enabled()) {
            return ActiveSpan.noop();
        }
        String spanId = AiSpanRecorder.newId();
        String parent = scope.currentSpanId();
        Instant start = Instant.now();
        scope.pushSpan(spanId);
        AiSpanDraft draft = AiSpanDraft.builder()
                .spanId(spanId)
                .traceId(scope.getTraceId())
                .parentSpanId(parent)
                .spanType(type)
                .name(name)
                .startedAt(start)
                .agent(agent())
                .node(node())
                .build();
        ActiveSpan active = new ActiveSpan(recorder, scope, draft, type == SpanType.AGENT);
        if (type == SpanType.AGENT) {
            CURRENT_AGENT_SPAN.set(active);
        }
        return active;
    }

    public static final class ActiveSpan implements AutoCloseable {
        private final AiSpanRecorder recorder;
        private final AiTraceScope scope;
        private final AiSpanDraft draft;
        private final boolean agentSpan;
        private boolean closed;
        private final boolean noop;

        private ActiveSpan(AiSpanRecorder recorder, AiTraceScope scope, AiSpanDraft draft, boolean agentSpan) {
            this.recorder = recorder;
            this.scope = scope;
            this.draft = draft;
            this.agentSpan = agentSpan;
            this.noop = false;
        }

        private ActiveSpan() {
            this.recorder = null;
            this.scope = null;
            this.draft = null;
            this.agentSpan = false;
            this.noop = true;
        }

        static ActiveSpan noop() {
            return new ActiveSpan();
        }

        public AiSpanDraft draft() {
            return draft;
        }

        public void ok() {
            if (!noop) {
                draft.setStatus(SpanStatus.OK);
            }
        }

        public void error(Throwable t) {
            if (noop) {
                return;
            }
            draft.setStatus(SpanStatus.ERROR);
            if (t != null) {
                draft.setErrorType(t.getClass().getSimpleName());
                draft.setErrorMessage(t.getMessage());
            }
        }

        public void error(String type, String message) {
            if (noop) {
                return;
            }
            draft.setStatus(SpanStatus.ERROR);
            draft.setErrorType(type);
            draft.setErrorMessage(message);
        }

        @Override
        public void close() {
            if (noop || closed) {
                return;
            }
            closed = true;
            if (agentSpan && CURRENT_AGENT_SPAN.get() == this) {
                CURRENT_AGENT_SPAN.remove();
            }
            scope.popSpan(draft.getSpanId());
            if (draft.getStatus() == null) {
                draft.setStatus(SpanStatus.OK);
            }
            draft.setEndedAt(Instant.now());
            if (draft.getAgent() == null && scope.getAttrs().get("agent") != null) {
                draft.setAgent(scope.getAttrs().get("agent"));
            }
            if (draft.getNode() == null && scope.getAttrs().get("node") != null) {
                draft.setNode(scope.getAttrs().get("node"));
            }
            recorder.record(draft);
        }
    }
}
