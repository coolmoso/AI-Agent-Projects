package org.example.qaagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class QualityEvaluator {
    private static final Logger log = LoggerFactory.getLogger(QualityEvaluator.class);

    private final LlmClient llmClient;
    private final ModelRouter modelRouter;
    private final String evaluationModel;

    public QualityEvaluator(
            LlmClient llmClient,
            ModelRouter modelRouter,
            @Value("${openai.models.evaluation:gpt-4}") String evaluationModel) {
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
        this.evaluationModel = evaluationModel;
    }

    /**
     * Evaluate answer quality using GPT-4
     * Returns a score between 0.0 and 1.0
     */
    public double evaluateQuality(String query, String context, String answer) {
        String prompt = String.format("""
            Evaluate the quality of the following answer based on the given context and question.
            
            Question: %s
            
            Context: %s
            
            Answer: %s
            
            Rate the answer on a scale of 0.0 to 1.0 based on:
            1. Relevance to the question
            2. Accuracy based on the provided context
            3. Completeness of the answer
            4. Clarity and coherence
            
            Provide only the numerical score (e.g., 0.85).
            """, query, context, answer);

        try {
            String response = llmClient.complete(prompt, 50, 0.1, evaluationModel);
            // Parse the score from the response
            String scoreStr = response.trim().replaceAll("[^0-9.]", "");
            if (scoreStr.isEmpty()) {
                log.warn("Could not parse quality score from response: {}", response);
                return 0.5;
            }
            double score = Double.parseDouble(scoreStr);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (IOException e) {
            log.error("Failed to evaluate answer quality", e);
            return 0.5; // Return neutral score on error
        }
    }

    /**
     * Evaluate faithfulness - does the answer stick to the provided context?
     */
    public double evaluateFaithfulness(String context, String answer) {
        String prompt = String.format("""
            Determine if the following answer is faithful to the provided context.
            The answer should not contain information not present in or inferable from the context.
            
            Context: %s
            
            Answer: %s
            
            Rate faithfulness on a scale of 0.0 to 1.0 where:
            - 1.0: Answer is completely faithful to context
            - 0.0: Answer contains significant hallucinations or unsupported information
            
            Provide only the numerical score (e.g., 0.85).
            """, context, answer);

        try {
            String response = llmClient.complete(prompt, 50, 0.1, evaluationModel);
            String scoreStr = response.trim().replaceAll("[^0-9.]", "");
            if (scoreStr.isEmpty()) {
                return 0.5;
            }
            double score = Double.parseDouble(scoreStr);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (IOException e) {
            log.error("Failed to evaluate faithfulness", e);
            return 0.5;
        }
    }

    /**
     * Evaluate if the answer appropriately addresses the user's question
     */
    public double evaluateRelevance(String query, String answer) {
        String prompt = String.format("""
            Evaluate how well the answer addresses the user's question.
            
            Question: %s
            
            Answer: %s
            
            Rate relevance on a scale of 0.0 to 1.0 where:
            - 1.0: Answer directly and completely addresses the question
            - 0.0: Answer is irrelevant or does not address the question
            
            Provide only the numerical score (e.g., 0.85).
            """, query, answer);

        try {
            String response = llmClient.complete(prompt, 50, 0.1, evaluationModel);
            String scoreStr = response.trim().replaceAll("[^0-9.]", "");
            if (scoreStr.isEmpty()) {
                return 0.5;
            }
            double score = Double.parseDouble(scoreStr);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (IOException e) {
            log.error("Failed to evaluate relevance", e);
            return 0.5;
        }
    }
}
