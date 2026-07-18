package com.interview.agent.observability.store;

import com.interview.agent.config.AppConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncSpanWriter {

    private final AiTraceRepository traceRepository;
    private final AiSpanRepository spanRepository;
    private final AppConfig appConfig;

    private BlockingQueue<Object> queue;
    private Thread worker;
    private volatile boolean running = true;
    private final AtomicLong dropped = new AtomicLong();

    @PostConstruct
    void start() {
        int capacity = Math.max(100, appConfig.getObservability().getQueueCapacity());
        queue = new LinkedBlockingQueue<>(capacity);
        worker = new Thread(this::loop, "ai-obs-writer");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    public boolean offer(Object entity) {
        if (!appConfig.getObservability().isEnabled()) {
            return false;
        }
        boolean ok = queue.offer(entity);
        if (!ok) {
            dropped.incrementAndGet();
            log.warn("[Obs] queue full, dropping event (totalDropped={})", dropped.get());
        }
        return ok;
    }

    public int queueSize() {
        return queue == null ? 0 : queue.size();
    }

    public int queueCapacity() {
        return appConfig.getObservability().getQueueCapacity();
    }

    public long droppedSpansTotal() {
        return dropped.get();
    }

    private void loop() {
        int batchSize = Math.max(1, appConfig.getObservability().getFlushBatchSize());
        List<Object> batch = new ArrayList<>(batchSize);
        while (running) {
            try {
                Object first = queue.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                flush(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[Obs] flush failed", e);
                batch.clear();
            }
        }
    }

    private void flush(List<Object> batch) {
        Map<String, AiTraceEntity> traces = new LinkedHashMap<>();
        List<AiSpanEntity> spans = new ArrayList<>();
        List<TraceEndUpdate> ends = new ArrayList<>();
        for (Object o : batch) {
            if (o instanceof AiTraceEntity t) {
                traces.put(t.getTraceId(), t);
            } else if (o instanceof AiSpanEntity s) {
                spans.add(s);
            } else if (o instanceof TraceEndUpdate u) {
                ends.add(u);
            }
        }
        for (TraceEndUpdate u : ends) {
            AiTraceEntity existing = traces.get(u.traceId());
            if (existing == null) {
                existing = traceRepository.findById(u.traceId()).orElse(null);
            }
            if (existing != null) {
                existing.setStatus(u.status());
                existing.setEndedAt(u.endedAt());
                existing.setErrorSummary(u.errorSummary());
                existing.setDroppedChildHint(u.droppedChildHint());
                traces.put(existing.getTraceId(), existing);
            }
        }
        if (!traces.isEmpty()) {
            traceRepository.saveAll(traces.values());
        }
        if (!spans.isEmpty()) {
            spanRepository.saveAll(spans);
        }
    }

    public record TraceEndUpdate(
            String traceId,
            String status,
            java.time.Instant endedAt,
            String errorSummary,
            Integer droppedChildHint
    ) {}
}
