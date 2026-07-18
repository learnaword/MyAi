package com.interview.agent.observability.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiTraceRepository extends JpaRepository<AiTraceEntity, String> {

    Page<AiTraceEntity> findByStartedAtGreaterThanEqualAndStartedAtLessThan(
            Instant from, Instant to, Pageable pageable);

    Page<AiTraceEntity> findByStartedAtGreaterThanEqualAndStartedAtLessThanAndScene(
            Instant from, Instant to, String scene, Pageable pageable);

    Page<AiTraceEntity> findByStartedAtGreaterThanEqualAndStartedAtLessThanAndSessionId(
            Instant from, Instant to, String sessionId, Pageable pageable);

    Page<AiTraceEntity> findByStartedAtGreaterThanEqualAndStartedAtLessThanAndSceneAndSessionId(
            Instant from, Instant to, String scene, String sessionId, Pageable pageable);

    Page<AiTraceEntity> findByStartedAtGreaterThanEqualAndStartedAtLessThanAndStatus(
            Instant from, Instant to, String status, Pageable pageable);

    @Query("""
            SELECT t FROM AiTraceEntity t
            WHERE t.startedAt >= :from AND t.startedAt < :to
              AND (:scene IS NULL OR t.scene = :scene)
              AND (:sessionId IS NULL OR t.sessionId = :sessionId)
              AND (:status IS NULL OR t.status = :status)
              AND (:userId IS NULL OR t.userId = :userId)
            """)
    Page<AiTraceEntity> search(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("scene") String scene,
            @Param("sessionId") String sessionId,
            @Param("status") String status,
            @Param("userId") Long userId,
            Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AiTraceEntity t WHERE t.startedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    List<AiTraceEntity> findBySessionId(String sessionId);
}
