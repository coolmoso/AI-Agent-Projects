package org.example.qaagent.model;

public record RetrievedChunk(
    String content,
    String sourceFile,
    int chunkIndex,
    double score
) {}
