package org.example.qaagent.model;

import java.util.List;

public record ChatResponse(
    String answer,
    List<RetrievedChunk> sources,
    String sessionId,
    boolean refused,
    String refusalReason,
    double confidenceScore,
    String traceId,
    long latencyMs,
    TokenUsage tokenUsage
) {}
