package com.interview.agent.observability.web;

import com.interview.agent.observability.query.ObservabilityBadRequestException;
import com.interview.agent.observability.query.ObservabilityNotFoundException;
import com.interview.agent.observability.query.ObservabilityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityQueryService queryService;

    @GetMapping("/traces/{traceId}")
    public Map<String, Object> getTrace(
            @PathVariable String traceId,
            @RequestParam(defaultValue = "false") boolean includeAttributes) {
        return queryService.getTrace(traceId, includeAttributes);
    }

    @GetMapping("/traces")
    public Map<String, Object> listTraces(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryService.listTraces(from, to, scene, sessionId, status, userId, page, size);
    }

    @GetMapping("/stats/tokens")
    public Map<String, Object> tokenStats(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) Long userId) {
        return queryService.tokenStats(from, to, scene, groupBy, userId);
    }

    @GetMapping("/stats/rag")
    public Map<String, Object> ragStats(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Long userId) {
        return queryService.ragStats(from, to, scene, userId);
    }

    @GetMapping("/stats/tools")
    public Map<String, Object> toolStats(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) Long userId) {
        return queryService.toolStats(from, to, toolName, userId);
    }

    @GetMapping("/stats/agents")
    public Map<String, Object> agentStats(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) String node,
            @RequestParam(required = false) String agent,
            @RequestParam(required = false) Long userId) {
        return queryService.agentStats(from, to, node, agent, userId);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return queryService.status();
    }

    @ExceptionHandler(ObservabilityNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ObservabilityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
    }

    @ExceptionHandler(ObservabilityBadRequestException.class)
    public ResponseEntity<Map<String, String>> badRequest(ObservabilityBadRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "BAD_REQUEST", "message", e.getMessage()));
    }
}
