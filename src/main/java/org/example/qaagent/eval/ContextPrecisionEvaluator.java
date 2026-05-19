package org.example.qaagent.eval;

import org.example.qaagent.model.RetrievedChunk;
import org.example.qaagent.service.LlmClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ContextPrecisionEvaluator {
    private final LlmClient llmClient;

    private static final String EVAL_PROMPT = """
        Given the QUERY and a list of retrieved CHUNKS, evaluate how many chunks are relevant.

        A chunk is RELEVANT if it contains information that helps answer the query.

        QUERY: %s

        CHUNKS:
        %s

        Output ONLY a JSON: {"total_chunks": N, "relevant_chunks": N, "precision": 0.XX}
        """;

    public ContextPrecisionEvaluator(LlmClient llmClient) { this.llmClient = llmClient; }

    public double evaluate(String query, List<RetrievedChunk> chunks) throws IOException {
        StringBuilder chunksStr = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            chunksStr.append(String.format("[Chunk %d]: %s\n\n", i + 1, chunks.get(i).content()));
        }
        String prompt = String.format(EVAL_PROMPT, query, chunksStr);
        String result = llmClient.complete(prompt, 200, 0.0);
        return parseScore(result, "precision");
    }

    private double parseScore(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx < 0) return 0.0;
        String after = json.substring(idx + field.length() + 3).replaceAll("[^0-9.]", "");
        try { return Double.parseDouble(after.substring(0, Math.min(4, after.length()))); }
        catch (Exception e) { return 0.0; }
    }
}
