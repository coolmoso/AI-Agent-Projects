package org.example.qaagent.eval;

import org.example.qaagent.service.LlmClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class StyleConsistencyEvaluator {
    private final LlmClient llmClient;

    private static final String EVAL_PROMPT = """
        Evaluate the ANSWER for style consistency against these criteria:
        1. Professional tone (no casual language, no emojis)
        2. Uses citations [Source: ...] when making claims
        3. Structured (uses bullet points or numbered lists for multi-part answers)
        4. Responds in the same language as the question
        5. Concise (no unnecessary repetition)

        QUESTION: %s
        ANSWER: %s

        Score each criterion 0-1, then output the average.
        Output ONLY a JSON: {"criteria_scores": [0.X, 0.X, 0.X, 0.X, 0.X], "average": 0.XX}
        """;

    public StyleConsistencyEvaluator(LlmClient llmClient) { this.llmClient = llmClient; }

    public double evaluate(String query, String answer) throws IOException {
        String prompt = String.format(EVAL_PROMPT, query, answer);
        String result = llmClient.complete(prompt, 200, 0.0);
        return parseScore(result);
    }

    private double parseScore(String json) {
        int idx = json.indexOf("\"average\"");
        if (idx < 0) return 0.0;
        String after = json.substring(idx + 10).replaceAll("[^0-9.]", "");
        try { return Double.parseDouble(after.substring(0, Math.min(4, after.length()))); }
        catch (Exception e) { return 0.0; }
    }
}
