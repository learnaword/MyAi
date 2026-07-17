package com.interview.agent.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "interview_sessions")
public class InterviewSessionEntity {
    @Id
    @Column(length = 64)
    private String id;

    private Long userId;

    @Column(length = 32)
    private String status;

    @Lob
    private String jdText;

    @Lob
    private String resumeText;

    @Lob
    private String matchReportJson;

    @Lob
    private String evaluationJson;

    @Lob
    private String reviewPlanJson;

    private Instant createdAt;
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "RUNNING";
        }
    }
}
