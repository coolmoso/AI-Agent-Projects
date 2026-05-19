package org.example.qaagent.eval;

import org.example.qaagent.service.LlmClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FaithfulnessEvaluator {
    private final LlmClient llmClient;

    private static final String EVAL_PROMPT = """
        You are an evaluation judge. Given a CONTEXT and an ANSWER, evaluate faithfulness.

        Faithfulness = (number of claims in the answer supported by the context) / (total claims in answer)

        Steps:
        1. List all factual claims in the ANSWER
        2. For each claim, check if it is supported by the CONTEXT
        3. Calculate the ratio

        CONTEXT:
        %s

        ANSWER:
        %s

        Output ONLY a JSON object: {"claims_total": N, "claims_supported": N, "score": 0.XX}
        """;

    public FaithfulnessEvaluator(LlmClient llmClient) { this.llmClient = llmClient; }

    public double evaluate(String context, String answer) throws IOException {
        String prompt = String.format(EVAL_PROMPT, context, answer);
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
