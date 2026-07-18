package com.interview.agent.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.config.AppConfig;
import com.interview.agent.observability.store.AiSpanEntity;
import com.interview.agent.observability.store.AiTraceEntity;
import com.interview.agent.observability.store.AsyncSpanWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiSpanRecorder {

    private final AsyncSpanWriter writer;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public boolean enabled() {
        return appConfig.getObservability().isEnabled();
    }

    public void persistTraceStart(AiTraceScope scope) {
        if (!enabled() || scope == null) {
            return;
        }
        AiTraceEntity entity = AiTraceEntity.builder()
                .traceId(scope.getTraceId())
                .scene(scope.getScene().name())
                .sessionId(scope.getSessionId())
                .wsSessionId(scope.getWsSessionId())
                .userId(scope.getUserId())
                .status("RUNNING")
                .startedAt(scope.getStartedAt())
                .droppedChildHint(0)
                .build();
        if (!writer.offer(entity)) {
            scope.getDroppedChildHint().incrementAndGet();
        }
    }

    public void persistTraceEnd(AiTraceScope scope, String status, String errorSummary) {
        if (!enabled() || scope == null) {
            return;
        }
        boolean ok = writer.offer(new AsyncSpanWriter.TraceEndUpdate(
                scope.getTraceId(),
                status,
                Instant.now(),
                truncate(errorSummary, 512),
                scope.getDroppedChildHint().get()
        ));
        if (!ok) {
            scope.getDroppedChildHint().incrementAndGet();
        }
    }

    public void record(AiSpanDraft draft) {
        if (!enabled() || draft == null) {
            return;
        }
        Instant end = draft.getEndedAt() == null ? Instant.now() : draft.getEndedAt();
        Instant start = draft.getStartedAt() == null ? end : draft.getStartedAt();
        Integer duration = draft.getDurationMs();
        if (duration == null) {
            duration = (int) Math.min(Integer.MAX_VALUE, Duration.between(start, end).toMillis());
        }
        String attrsJson = null;
        Map<String, Object> attrs = draft.getAttributes();
        if (attrs != null && !attrs.isEmpty()) {
            try {
                attrsJson = objectMapper.writeValueAsString(attrs);
            } catch (Exception e) {
                log.debug("[Obs] attributes serialize failed: {}", e.getMessage());
            }
        }
        AiSpanEntity entity = AiSpanEntity.builder()
                .spanId(draft.getSpanId())
                .traceId(draft.getTraceId())
                .parentSpanId(draft.getParentSpanId())
                .spanType(draft.getSpanType().name())
                .name(draft.getName())
                .status(draft.getStatus() == null ? SpanStatus.UNSET.name() : draft.getStatus().name())
                .startedAt(start)
                .endedAt(end)
                .durationMs(duration)
                .agent(draft.getAgent())
                .node(draft.getNode())
                .model(draft.getModel())
                .toolName(draft.getToolName())
                .promptTokens(draft.getPromptTokens())
                .completionTokens(draft.getCompletionTokens())
                .totalTokens(draft.getTotalTokens())
                .costAmount(draft.getCostAmount())
                .costCurrency(draft.getCostCurrency())
                .ragCandidateCount(draft.getRagCandidateCount())
                .ragEmpty(draft.getRagEmpty())
                .ragHit(draft.getRagHit())
                .ragReranked(draft.getRagReranked())
                .ragRerankFallback(draft.getRagRerankFallback())
                .errorType(draft.getErrorType())
                .errorMessage(truncate(draft.getErrorMessage(), 512))
                .attributesJson(attrsJson)
                .build();
        if (!writer.offer(entity) && AiTraceContext.current() != null) {
            AiTraceContext.current().getDroppedChildHint().incrementAndGet();
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
