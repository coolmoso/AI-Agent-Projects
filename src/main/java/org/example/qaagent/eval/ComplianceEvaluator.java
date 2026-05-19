package org.example.qaagent.eval;

import org.example.qaagent.service.LlmClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ComplianceEvaluator {
    private final LlmClient llmClient;

    private static final String EVAL_PROMPT = """
        You are an evaluation judge. Compare the GENERATED answer to the REFERENCE answer.

        Criteria:
        - Does the generated answer address the same question?
        - Are the key facts correct?
        - Is important information missing?

        QUESTION: %s
        REFERENCE ANSWER: %s
        GENERATED ANSWER: %s

        Output ONLY a JSON: {"compliant": true, "score": 0.XX, "reason": "..."}
        """;

    public ComplianceEvaluator(LlmClient llmClient) { this.llmClient = llmClient; }

    public double evaluate(String query, String reference, String generated) throws IOException {
        String prompt = String.format(EVAL_PROMPT, query, reference, generated);
        String result = llmClient.complete(prompt, 200, 0.0);
        return parseScore(result);
    }

    private double parseScore(String json) {
        int idx = json.indexOf("\"score\"");
        if (idx < 0) return 0.0;
        String after = json.substring(idx + 8).replaceAll("[^0-9.]", "");
        try { return Double.parseDouble(after.substring(0, Math.min(4, after.length()))); }
        catch (Exception e) { return 0.0; }
    }
}
