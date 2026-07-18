package com.interview.agent.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weakness_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_weakness_user_topic", columnNames = {"user_id", "topic"})
})
public class WeaknessRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 128)
    private String topic;

    private int hitCount;

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
