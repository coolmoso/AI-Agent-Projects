package org.example.qaagent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank String query,
    String sessionId,
    @Size(max = 50) String userId
) {}
