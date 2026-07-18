package com.interview.agent.observability;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class AiSpanDraft {
    private String spanId;
    private String traceId;
    private String parentSpanId;
    private SpanType spanType;
    private String name;
    private SpanStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private Integer durationMs;
    private String agent;
    private String node;
    private String model;
    private String toolName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal costAmount;
    private String costCurrency;
    private Integer ragCandidateCount;
    private Boolean ragEmpty;
    private Boolean ragHit;
    private Boolean ragReranked;
    private Boolean ragRerankFallback;
    private String errorType;
    private String errorMessage;
    @Builder.Default
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public Map<String, Object> getAttributes() {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        return attributes;
    }
}
