package org.example.qaagent.service;

import org.example.qaagent.model.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class RetrieverRouter {
    private final VectorRetriever vectorRetriever;
    private final EnsembleRetriever ensembleRetriever;
    private final String mode;

    public RetrieverRouter(VectorRetriever vectorRetriever,
                            EnsembleRetriever ensembleRetriever,
                            @Value("${retrieval.mode:ensemble}") String mode) {
        this.vectorRetriever = vectorRetriever;
        this.ensembleRetriever = ensembleRetriever;
        this.mode = mode;
    }

    public List<RetrievedChunk> retrieve(String query, float[] queryVector, int topK) throws IOException {
        return switch (mode) {
            case "vector" -> vectorRetriever.search(queryVector, topK);
            case "ensemble" -> ensembleRetriever.search(query, queryVector, topK);
            default -> throw new IllegalArgumentException("Unknown retrieval mode: " + mode);
        };
    }
}
