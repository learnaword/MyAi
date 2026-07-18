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

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_trace")
public class AiTraceEntity {

    @Id
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(nullable = false, length = 32)
    private String scene;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "ws_session_id", length = 128)
    private String wsSessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "error_summary", length = 512)
    private String errorSummary;

    @Column(name = "dropped_child_hint")
    private Integer droppedChildHint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "RUNNING";
        }
        if (droppedChildHint == null) {
            droppedChildHint = 0;
        }
    }
}
