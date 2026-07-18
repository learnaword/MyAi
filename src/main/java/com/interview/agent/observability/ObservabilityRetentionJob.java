package com.interview.agent.observability;

import com.interview.agent.config.AppConfig;
import com.interview.agent.observability.store.AiSpanRepository;
import com.interview.agent.observability.store.AiTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObservabilityRetentionJob {

    private final AppConfig appConfig;
    private final AiTraceRepository traceRepository;
    private final AiSpanRepository spanRepository;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanup() {
        if (!appConfig.getObservability().isEnabled()) {
            return;
        }
        int days = Math.max(1, appConfig.getObservability().getRetainDays());
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int spans = spanRepository.deleteOlderThan(cutoff);
        int traces = traceRepository.deleteOlderThan(cutoff);
        if (spans > 0 || traces > 0) {
            log.info("[Obs] retention cleanup days={} cutoff={} deletedSpans={} deletedTraces={}",
                    days, cutoff, spans, traces);
        }
    }
}
