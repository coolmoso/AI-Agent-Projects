package org.example.qaagent.service;

import org.example.qaagent.model.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConfidenceScorer {

    private final double scoreThreshold;

    public ConfidenceScorer(@Value("${safety.confidence-threshold:0.35}") double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    public double score(String query, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) return 0.0;

        double topScore = chunks.get(0).score();
        double avgScore = chunks.stream().mapToDouble(RetrievedChunk::score).average().orElse(0.0);
        double lastScore = chunks.get(chunks.size() - 1).score();
        double scoreGap = topScore - lastScore;

        double confidence = 0.5 * topScore + 0.3 * avgScore + 0.2 * scoreGap;
        return Math.min(1.0, Math.max(0.0, confidence));
    }

    public double getThreshold() { return scoreThreshold; }
}
