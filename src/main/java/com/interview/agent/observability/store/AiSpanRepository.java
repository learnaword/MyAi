package com.interview.agent.observability.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiSpanRepository extends JpaRepository<AiSpanEntity, String> {

    List<AiSpanEntity> findByTraceIdOrderByStartedAtAscSpanIdAsc(String traceId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AiSpanEntity s WHERE s.startedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT s FROM AiSpanEntity s
            WHERE s.spanType = 'LLM'
              AND s.startedAt >= :from AND s.startedAt < :to
              AND (:scene IS NULL OR EXISTS (
                   SELECT 1 FROM AiTraceEntity t WHERE t.traceId = s.traceId AND t.scene = :scene))
            """)
    List<AiSpanEntity> findLlmSpans(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("scene") String scene);

    @Query("""
            SELECT s FROM AiSpanEntity s
            WHERE s.spanType = 'RAG'
              AND s.startedAt >= :from AND s.startedAt < :to
              AND (:scene IS NULL OR EXISTS (
                   SELECT 1 FROM AiTraceEntity t WHERE t.traceId = s.traceId AND t.scene = :scene))
            """)
    List<AiSpanEntity> findRagSpans(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("scene") String scene);

    @Query("""
            SELECT s FROM AiSpanEntity s
            WHERE s.spanType = 'TOOL'
              AND s.startedAt >= :from AND s.startedAt < :to
              AND (:toolName IS NULL OR s.toolName = :toolName)
            """)
    List<AiSpanEntity> findToolSpans(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("toolName") String toolName);

    @Query("""
            SELECT s FROM AiSpanEntity s
            WHERE s.spanType = 'AGENT'
              AND s.startedAt >= :from AND s.startedAt < :to
              AND (:node IS NULL OR s.node = :node)
              AND (:agent IS NULL OR s.agent = :agent)
            """)
    List<AiSpanEntity> findAgentSpans(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("node") String node,
            @Param("agent") String agent);
}
