package org.example.qaagent.model;

public record IngestionRequest(
    String sourceType,
    String language
) {}
