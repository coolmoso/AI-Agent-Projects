package org.example.qaagent.service;

import org.example.qaagent.model.MetricsReport;
import org.example.qaagent.model.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class MetricsCollector {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successCount = new AtomicLong();
    private final AtomicLong refusalCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong totalPromptTokens = new AtomicLong();
    private final AtomicLong totalCompletionTokens = new AtomicLong();

    private static final int LATENCY_BUFFER_SIZE = 10_000;
    private final long[] latencies = new long[LATENCY_BUFFER_SIZE];
    private int latencyIndex = 0;
    private int latencyCount = 0;
    private final ReentrantLock latencyLock = new ReentrantLock();

    public void recordSuccess(long latencyMs, TokenUsage usage) {
        totalRequests.incrementAndGet();
        successCount.incrementAndGet();
        recordLatency(latencyMs);
        if (usage != null) {
            totalPromptTokens.addAndGet(usage.promptTokens());
            totalCompletionTokens.addAndGet(usage.completionTokens());
        }
    }

    public void recordRefusal() {
        totalRequests.incrementAndGet();
        refusalCount.incrementAndGet();
    }

    public void recordError() {
        totalRequests.incrementAndGet();
        errorCount.incrementAndGet();
    }

    public void recordCacheHit() { cacheHits.incrementAndGet(); }
    public void recordCacheMiss() { cacheMisses.incrementAndGet(); }

    public void recordLatency(long latencyMs) {
        latencyLock.lock();
        try {
            latencies[latencyIndex % LATENCY_BUFFER_SIZE] = latencyMs;
            latencyIndex++;
            latencyCount = Math.min(latencyCount + 1, LATENCY_BUFFER_SIZE);
        } finally {
            latencyLock.unlock();
        }
    }

    public MetricsReport generateReport() {
        long total = totalRequests.get();
        double refusalRate = total > 0 ? (double) refusalCount.get() / total : 0;
        long totalCacheRequests = cacheHits.get() + cacheMisses.get();
        double cacheHitRate = totalCacheRequests > 0 ? (double) cacheHits.get() / totalCacheRequests : 0;

        long p50 = 0, p95 = 0;
        latencyLock.lock();
        try {
            if (latencyCount > 0) {
                long[] sorted = Arrays.copyOf(latencies, latencyCount);
                Arrays.sort(sorted);
                p50 = sorted[(int)(latencyCount * 0.50)];
                p95 = sorted[Math.min((int)(latencyCount * 0.95), latencyCount - 1)];
            }
        } finally {
            latencyLock.unlock();
        }

        return new MetricsReport(
            total, successCount.get(), refusalCount.get(), errorCount.get(),
            p50, p95,
            totalPromptTokens.get(), totalCompletionTokens.get(),
            cacheHitRate, refusalRate
        );
    }

    public String exportCSV() {
        MetricsReport r = generateReport();
        return String.format("""
            metric,value
            total_requests,%d
            success_count,%d
            refusal_count,%d
            error_count,%d
            p50_latency_ms,%d
            p95_latency_ms,%d
            total_prompt_tokens,%d
            total_completion_tokens,%d
            cache_hit_rate,%.4f
            refusal_rate,%.4f
            """,
            r.totalRequests(), r.successCount(), r.refusalCount(), r.errorCount(),
            r.p50LatencyMs(), r.p95LatencyMs(),
            r.totalPromptTokens(), r.totalCompletionTokens(),
            r.cacheHitRate(), r.refusalRate()
        );
    }
}
