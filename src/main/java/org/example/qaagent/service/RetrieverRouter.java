package org.example.qaagent.service;

import org.example.qaagent.model.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class RetrieverRouter {
    private final VectorRetriever vectorRetriever;
    private final HybridRetriever hybridRetriever;
    private final String mode;

    public RetrieverRouter(VectorRetriever vectorRetriever,
                            HybridRetriever hybridRetriever,
                            @Value("${retrieval.mode:hybrid}") String mode) {
        this.vectorRetriever = vectorRetriever;
        this.hybridRetriever = hybridRetriever;
        this.mode = mode;
    }

    public List<RetrievedChunk> retrieve(String query, float[] queryVector, int topK) throws IOException {
        return switch (mode) {
            case "vector" -> vectorRetriever.search(queryVector, topK);
            case "hybrid" -> hybridRetriever.search(query, queryVector, topK);
            default -> throw new IllegalArgumentException("Unknown retrieval mode: " + mode);
        };
    }
}
