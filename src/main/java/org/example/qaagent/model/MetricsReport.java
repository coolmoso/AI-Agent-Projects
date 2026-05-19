package org.example.qaagent.model;

public record MetricsReport(
    long totalRequests,
    long successCount,
    long refusalCount,
    long errorCount,
    long p50LatencyMs,
    long p95LatencyMs,
    long totalPromptTokens,
    long totalCompletionTokens,
    double cacheHitRate,
    double refusalRate
) {}
