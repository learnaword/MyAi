package com.interview.agent.observability.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.agent.observability.store.AiSpanEntity;
import com.interview.agent.observability.store.AiSpanRepository;
import com.interview.agent.observability.store.AiTraceEntity;
import com.interview.agent.observability.store.AiTraceRepository;
import com.interview.agent.observability.store.AsyncSpanWriter;
import com.interview.agent.config.AppConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObservabilityQueryService {

    private static final long MAX_RANGE_SECONDS = 31L * 24 * 3600;

    private final AiTraceRepository traceRepository;
    private final AiSpanRepository spanRepository;
    private final AsyncSpanWriter asyncSpanWriter;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getTrace(String traceId, boolean includeAttributes) {
        AiTraceEntity trace = traceRepository.findById(traceId)
                .orElseThrow(() -> new ObservabilityNotFoundException("trace not found"));
        List<AiSpanEntity> spans = spanRepository.findByTraceIdOrderByStartedAtAscSpanIdAsc(traceId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("traceId", trace.getTraceId());
        body.put("scene", trace.getScene());
        body.put("sessionId", trace.getSessionId());
        body.put("wsSessionId", trace.getWsSessionId());
        body.put("userId", trace.getUserId());
        body.put("status", trace.getStatus());
        body.put("startedAt", trace.getStartedAt());
        body.put("endedAt", trace.getEndedAt());
        body.put("errorSummary", trace.getErrorSummary());
        body.put("droppedChildHint", trace.getDroppedChildHint());
        body.put("spans", spans.stream().map(s -> toSpanMap(s, includeAttributes)).toList());
        return body;
    }

    public Map<String, Object> listTraces(
            Instant from, Instant to, String scene, String sessionId, String status, Long userId, int page, int size) {
        validateRange(from, to);
        int pageSize = Math.min(Math.max(size, 1), 100);
        PageRequest pr = PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<AiTraceEntity> result = traceRepository.search(from, to, blankToNull(scene), blankToNull(sessionId),
                blankToNull(status), userId, pr);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        body.put("content", result.getContent().stream().map(this::toTraceSummary).toList());
        return body;
    }

    public Map<String, Object> tokenStats(Instant from, Instant to, String scene, String groupByRaw, Long userId) {
        validateRange(from, to);
        Set<String> groupBy = parseGroupBy(groupByRaw, Set.of("agent", "model"));
        List<AiSpanEntity> spans = spanRepository.findLlmSpans(from, to, blankToNull(scene), userId);
        Map<String, Agg> groups = new LinkedHashMap<>();
        for (AiSpanEntity s : spans) {
            String key = groupKey(groupBy, s, scene);
            Agg agg = groups.computeIfAbsent(key, k -> new Agg());
            agg.llmCalls++;
            agg.sumPrompt += s.getPromptTokens() == null ? 0 : s.getPromptTokens();
            agg.sumCompletion += s.getCompletionTokens() == null ? 0 : s.getCompletionTokens();
            agg.sumTotal += s.getTotalTokens() == null ? 0 : s.getTotalTokens();
            if (s.getCostAmount() != null) {
                agg.sumCost = agg.sumCost.add(s.getCostAmount());
            }
            if (s.getPromptTokens() == null) {
                agg.usageMissing++;
            }
            if (agg.costCurrency == null) {
                agg.costCurrency = s.getCostCurrency();
            }
            agg.sample = s;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Agg totals = new Agg();
        for (Map.Entry<String, Agg> e : groups.entrySet()) {
            Agg a = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agent", groupBy.contains("agent") ? a.sample.getAgent() : null);
            row.put("node", groupBy.contains("node") ? a.sample.getNode() : null);
            row.put("model", groupBy.contains("model") ? a.sample.getModel() : null);
            row.put("scene", groupBy.contains("scene") ? scene : null);
            row.put("llmCalls", a.llmCalls);
            row.put("sumPromptTokens", a.sumPrompt);
            row.put("sumCompletionTokens", a.sumCompletion);
            row.put("sumTotalTokens", a.sumTotal);
            row.put("sumCostAmount", a.sumCost);
            row.put("costCurrency", a.costCurrency == null ? appConfig.getObservability().getCostCurrency() : a.costCurrency);
            row.put("usageMissingCount", a.usageMissing);
            rows.add(row);
            totals.add(a);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", to);
        body.put("groupBy", new ArrayList<>(groupBy));
        body.put("rows", rows);
        Map<String, Object> totalMap = new LinkedHashMap<>();
        totalMap.put("llmCalls", totals.llmCalls);
        totalMap.put("sumPromptTokens", totals.sumPrompt);
        totalMap.put("sumCompletionTokens", totals.sumCompletion);
        totalMap.put("sumTotalTokens", totals.sumTotal);
        totalMap.put("sumCostAmount", totals.sumCost);
        totalMap.put("costCurrency", appConfig.getObservability().getCostCurrency());
        totalMap.put("usageMissingCount", totals.usageMissing);
        body.put("totals", totalMap);
        return body;
    }

    public Map<String, Object> ragStats(Instant from, Instant to, String scene, Long userId) {
        validateRange(from, to);
        List<AiSpanEntity> spans = spanRepository.findRagSpans(from, to, blankToNull(scene), userId);
        int retrieves = spans.size();
        long empty = spans.stream().filter(s -> Boolean.TRUE.equals(s.getRagEmpty())).count();
        long hit = spans.stream().filter(s -> Boolean.TRUE.equals(s.getRagHit())).count();
        long rerank = spans.stream().filter(s -> Boolean.TRUE.equals(s.getRagReranked())).count();
        long fallback = spans.stream().filter(s -> Boolean.TRUE.equals(s.getRagRerankFallback())).count();
        double avg = spans.stream()
                .filter(s -> s.getDurationMs() != null)
                .mapToInt(AiSpanEntity::getDurationMs)
                .average()
                .orElse(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", to);
        body.put("retrieves", retrieves);
        body.put("emptyCount", empty);
        body.put("hitCount", hit);
        body.put("emptyRate", rate(empty, retrieves));
        body.put("hitRate", rate(hit, retrieves));
        body.put("rerankCount", rerank);
        body.put("rerankRate", rate(rerank, retrieves));
        body.put("rerankFallbackCount", fallback);
        body.put("avgDurationMs", avg);
        return body;
    }

    public Map<String, Object> toolStats(Instant from, Instant to, String toolName, Long userId) {
        validateRange(from, to);
        List<AiSpanEntity> spans = spanRepository.findToolSpans(from, to, blankToNull(toolName), userId);
        Map<String, List<AiSpanEntity>> byTool = spans.stream()
                .collect(Collectors.groupingBy(s -> s.getToolName() == null ? "unknown" : s.getToolName(),
                        LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, List<AiSpanEntity>> e : byTool.entrySet()) {
            List<AiSpanEntity> list = e.getValue();
            long ok = list.stream().filter(s -> "OK".equals(s.getStatus())).count();
            long err = list.stream().filter(s -> "ERROR".equals(s.getStatus())).count();
            double avg = list.stream().filter(s -> s.getDurationMs() != null)
                    .mapToInt(AiSpanEntity::getDurationMs).average().orElse(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("toolName", e.getKey());
            row.put("calls", list.size());
            row.put("okCount", ok);
            row.put("errorCount", err);
            row.put("successRate", rate(ok, list.size()));
            row.put("avgDurationMs", avg);
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", to);
        body.put("rows", rows);
        return body;
    }

    public Map<String, Object> agentStats(Instant from, Instant to, String node, String agent, Long userId) {
        validateRange(from, to);
        List<AiSpanEntity> spans = spanRepository.findAgentSpans(from, to, blankToNull(node), blankToNull(agent), userId);
        Map<String, List<AiSpanEntity>> grouped = spans.stream()
                .collect(Collectors.groupingBy(
                        s -> (s.getNode() == null ? "" : s.getNode()) + "|" + (s.getAgent() == null ? "" : s.getAgent()),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (List<AiSpanEntity> list : grouped.values()) {
            AiSpanEntity sample = list.get(0);
            long ok = list.stream().filter(s -> "OK".equals(s.getStatus())).count();
            long err = list.stream().filter(s -> "ERROR".equals(s.getStatus())).count();
            double avg = list.stream().filter(s -> s.getDurationMs() != null)
                    .mapToInt(AiSpanEntity::getDurationMs).average().orElse(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("node", sample.getNode());
            row.put("agent", sample.getAgent());
            row.put("runs", list.size());
            row.put("okCount", ok);
            row.put("errorCount", err);
            row.put("successRate", rate(ok, list.size()));
            row.put("avgDurationMs", avg);
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", to);
        body.put("groupBy", List.of("node"));
        body.put("rows", rows);
        return body;
    }

    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", appConfig.getObservability().isEnabled());
        body.put("adminConfigured", adminConfigured());
        body.put("retainDays", appConfig.getObservability().getRetainDays());
        body.put("queueCapacity", asyncSpanWriter.queueCapacity());
        body.put("queueSize", asyncSpanWriter.queueSize());
        body.put("droppedSpansTotal", asyncSpanWriter.droppedSpansTotal());
        return body;
    }

    public boolean adminConfigured() {
        // Observability admin auth is ADMIN JWT (shared admin-token removed)
        return true;
    }

    public void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ObservabilityBadRequestException("from/to required and from must be before to");
        }
        if (DurationSeconds(from, to) > MAX_RANGE_SECONDS) {
            throw new ObservabilityBadRequestException("time range must be <= 31 days");
        }
    }

    private static long DurationSeconds(Instant from, Instant to) {
        return to.getEpochSecond() - from.getEpochSecond();
    }

    private Map<String, Object> toTraceSummary(AiTraceEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId", t.getTraceId());
        m.put("scene", t.getScene());
        m.put("sessionId", t.getSessionId());
        m.put("userId", t.getUserId());
        m.put("status", t.getStatus());
        m.put("startedAt", t.getStartedAt());
        m.put("endedAt", t.getEndedAt());
        m.put("errorSummary", t.getErrorSummary());
        return m;
    }

    private Map<String, Object> toSpanMap(AiSpanEntity s, boolean includeAttributes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("spanId", s.getSpanId());
        m.put("parentSpanId", s.getParentSpanId());
        m.put("spanType", s.getSpanType());
        m.put("name", s.getName());
        m.put("status", s.getStatus());
        m.put("startedAt", s.getStartedAt());
        m.put("endedAt", s.getEndedAt());
        m.put("durationMs", s.getDurationMs());
        m.put("agent", s.getAgent());
        m.put("node", s.getNode());
        m.put("model", s.getModel());
        m.put("toolName", s.getToolName());
        m.put("promptTokens", s.getPromptTokens());
        m.put("completionTokens", s.getCompletionTokens());
        m.put("totalTokens", s.getTotalTokens());
        m.put("costAmount", s.getCostAmount());
        m.put("costCurrency", s.getCostCurrency());
        m.put("ragCandidateCount", s.getRagCandidateCount());
        m.put("ragEmpty", s.getRagEmpty());
        m.put("ragHit", s.getRagHit());
        m.put("ragReranked", s.getRagReranked());
        m.put("ragRerankFallback", s.getRagRerankFallback());
        m.put("errorType", s.getErrorType());
        m.put("errorMessage", s.getErrorMessage());
        if (includeAttributes && s.getAttributesJson() != null) {
            try {
                m.put("attributes", objectMapper.readValue(s.getAttributesJson(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                m.put("attributes", Map.of("raw", s.getAttributesJson()));
            }
        } else {
            m.put("attributes", null);
        }
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static double rate(long num, long den) {
        if (den <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(num).divide(BigDecimal.valueOf(den), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static Set<String> parseGroupBy(String raw, Set<String> defaults) {
        if (raw == null || raw.isBlank()) {
            return defaults;
        }
        Set<String> allowed = Set.of("agent", "node", "model", "scene");
        Set<String> result = java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(allowed::contains)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return result.isEmpty() ? defaults : result;
    }

    private static String groupKey(Set<String> groupBy, AiSpanEntity s, String sceneFilter) {
        StringBuilder sb = new StringBuilder();
        if (groupBy.contains("agent")) {
            sb.append(s.getAgent()).append('|');
        }
        if (groupBy.contains("node")) {
            sb.append(s.getNode()).append('|');
        }
        if (groupBy.contains("model")) {
            sb.append(s.getModel()).append('|');
        }
        if (groupBy.contains("scene")) {
            sb.append(sceneFilter).append('|');
        }
        return sb.toString();
    }

    private static final class Agg {
        int llmCalls;
        long sumPrompt;
        long sumCompletion;
        long sumTotal;
        BigDecimal sumCost = BigDecimal.ZERO;
        int usageMissing;
        String costCurrency;
        AiSpanEntity sample;

        void add(Agg o) {
            llmCalls += o.llmCalls;
            sumPrompt += o.sumPrompt;
            sumCompletion += o.sumCompletion;
            sumTotal += o.sumTotal;
            sumCost = sumCost.add(o.sumCost);
            usageMissing += o.usageMissing;
        }
    }
}
