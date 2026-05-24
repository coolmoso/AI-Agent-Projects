package org.example.qaagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ModelRouter {
    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final String primaryModel;
    private final String fallbackModel;
    private final String evaluationModel;
    private final boolean routingEnabled;
    private final double complexityThreshold;
    private final double confidenceThreshold;
    private final int maxConcurrentFallback;
    private final AtomicInteger currentFallbackUsage = new AtomicInteger(0);

    public ModelRouter(
            @Value("${openai.models.primary:gpt-4o}") String primaryModel,
            @Value("${openai.models.fallback:gpt-3.5-turbo}") String fallbackModel,
            @Value("${openai.models.evaluation:gpt-4}") String evaluationModel,
            @Value("${openai.routing.enabled:true}") boolean routingEnabled,
            @Value("${openai.routing.complexity-threshold:0.5}") double complexityThreshold,
            @Value("${openai.routing.confidence-threshold:0.7}") double confidenceThreshold,
            @Value("${openai.routing.max-concurrent-fallback:10}") int maxConcurrentFallback) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.evaluationModel = evaluationModel;
        this.routingEnabled = routingEnabled;
        this.complexityThreshold = complexityThreshold;
        this.confidenceThreshold = confidenceThreshold;
        this.maxConcurrentFallback = maxConcurrentFallback;
    }

    public enum ModelPurpose {
        CHAT,
        EVALUATION,
        REWRITING,
        RERANKING
    }

    /**
     * Select the appropriate model based on query characteristics and system state
     */
    public String selectModel(String query, double confidence, double complexity, ModelPurpose purpose) {
        if (!routingEnabled) {
            return getDefaultModel(purpose);
        }

        switch (purpose) {
            case EVALUATION:
                return evaluationModel;
            case REWRITING:
            case RERANKING:
                return fallbackModel; // Use faster model for preprocessing
            case CHAT:
                return selectChatModel(query, confidence, complexity);
            default:
                return primaryModel;
        }
    }

    /**
     * Select model for chat generation based on query analysis
     */
    private String selectChatModel(String query, double confidence, double complexity) {
        // High confidence + low complexity: use fallback for speed
        if (confidence >= confidenceThreshold && complexity < complexityThreshold) {
            if (currentFallbackUsage.get() < maxConcurrentFallback) {
                log.debug("Using fallback model: high confidence ({}) and low complexity ({})", 
                    confidence, complexity);
                return fallbackModel;
            }
        }

        // Low confidence or high complexity: use primary model for quality
        if (confidence < confidenceThreshold || complexity >= complexityThreshold) {
            log.debug("Using primary model: low confidence ({}) or high complexity ({})", 
                confidence, complexity);
            return primaryModel;
        }

        // Default to primary model
        return primaryModel;
    }

    /**
     * Calculate query complexity score (0.0 to 1.0)
     * Higher score indicates more complex query
     */
    public double calculateComplexity(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }

        double complexity = 0.0;
        int length = query.length();

        // Length factor (longer queries tend to be more complex)
        if (length > 200) complexity += 0.3;
        else if (length > 100) complexity += 0.15;

        // Question complexity indicators
        String lowerQuery = query.toLowerCase();
        
        // Multi-part questions
        if (lowerQuery.contains(" and ") || lowerQuery.contains(" or ") || 
            lowerQuery.contains(" also ") || lowerQuery.contains(" additionally ")) {
            complexity += 0.25;
        }

        // Comparative questions
        if (lowerQuery.contains("compare") || lowerQuery.contains("difference") ||
            lowerQuery.contains("versus") || lowerQuery.contains(" vs ")) {
            complexity += 0.2;
        }

        // Procedural questions
        if (lowerQuery.contains("how to") || lowerQuery.contains("steps to") ||
            lowerQuery.contains("process") || lowerQuery.contains("procedure")) {
            complexity += 0.15;
        }

        // Technical terms
        String technicalTerms = "api|database|authentication|encryption|compliance|regulation|architecture|implementation|integration|deployment";
        if (lowerQuery.matches(".*(" + technicalTerms + ").*")) {
            complexity += 0.2;
        }

        // Conditional logic
        if (lowerQuery.contains("if ") || lowerQuery.contains("when ") ||
            lowerQuery.contains("depending on") || lowerQuery.contains("condition")) {
            complexity += 0.15;
        }

        // Numerical or data-specific questions
        if (lowerQuery.matches(".*\\d+.*") || lowerQuery.contains("percentage") ||
            lowerQuery.contains("ratio") || lowerQuery.contains("rate")) {
            complexity += 0.1;
        }

        return Math.min(complexity, 1.0);
    }

    /**
     * Get default model for a given purpose
     */
    public String getDefaultModel(ModelPurpose purpose) {
        switch (purpose) {
            case EVALUATION:
                return evaluationModel;
            case REWRITING:
            case RERANKING:
                return fallbackModel;
            case CHAT:
            default:
                return primaryModel;
        }
    }

    /**
     * Get primary model
     */
    public String getPrimaryModel() {
        return primaryModel;
    }

    /**
     * Get fallback model
     */
    public String getFallbackModel() {
        return fallbackModel;
    }

    /**
     * Get evaluation model
     */
    public String getEvaluationModel() {
        return evaluationModel;
    }

    /**
     * Track fallback model usage
     */
    public void incrementFallbackUsage() {
        currentFallbackUsage.incrementAndGet();
    }

    public void decrementFallbackUsage() {
        currentFallbackUsage.decrementAndGet();
    }

    public int getCurrentFallbackUsage() {
        return currentFallbackUsage.get();
    }
}
