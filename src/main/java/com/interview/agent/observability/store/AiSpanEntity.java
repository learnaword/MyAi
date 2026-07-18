package com.interview.agent.observability.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_span")
public class AiSpanEntity {

    @Id
    @Column(name = "span_id", length = 64)
    private String spanId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "parent_span_id", length = 64)
    private String parentSpanId;

    @Column(name = "span_type", nullable = false, length = 16)
    private String spanType;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(length = 64)
    private String agent;

    @Column(length = 64)
    private String node;

    @Column(length = 64)
    private String model;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "cost_amount", precision = 18, scale = 8)
    private BigDecimal costAmount;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency;

    @Column(name = "rag_candidate_count")
    private Integer ragCandidateCount;

    @Column(name = "rag_empty")
    private Boolean ragEmpty;

    @Column(name = "rag_hit")
    private Boolean ragHit;

    @Column(name = "rag_reranked")
    private Boolean ragReranked;

    @Column(name = "rag_rerank_fallback")
    private Boolean ragRerankFallback;

    @Column(name = "error_type", length = 128)
    private String errorType;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_json", columnDefinition = "json")
    private String attributesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
