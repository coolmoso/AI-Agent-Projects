package org.example.qaagent.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.example.qaagent.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationManager conversationManager;
    private final QueryRewriter queryRewriter;
    private final EmbeddingService embeddingService;
    private final RetrieverRouter retrieverRouter;
    private final RerankerService rerankerService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final ConfidenceScorer confidenceScorer;
    private final SafetyChecker safetyChecker;
    private final PiiRedactor piiRedactor;
    private final MetricsCollector metricsCollector;
    private final Cache<String, ChatResponse> answerCache;
    private final ModelRouter modelRouter;
    private final QualityEvaluator qualityEvaluator;

    private final int topK;
    private final int finalK;
    private final int maxTokens;
    private final double temperature;

    public ChatService(ConversationManager conversationManager,
                        QueryRewriter queryRewriter,
                        EmbeddingService embeddingService,
                        RetrieverRouter retrieverRouter,
                        RerankerService rerankerService,
                        PromptBuilder promptBuilder,
                        LlmClient llmClient,
                        ConfidenceScorer confidenceScorer,
                        SafetyChecker safetyChecker,
                        PiiRedactor piiRedactor,
                        MetricsCollector metricsCollector,
                        Cache<String, ChatResponse> answerCache,
                        ModelRouter modelRouter,
                        QualityEvaluator qualityEvaluator,
                        @Value("${retrieval.top-k:10}") int topK,
                        @Value("${retrieval.final-k:5}") int finalK,
                        @Value("${llm.max-tokens:1024}") int maxTokens,
                        @Value("${llm.temperature:0.3}") double temperature) {
        this.conversationManager = conversationManager;
        this.queryRewriter = queryRewriter;
        this.embeddingService = embeddingService;
        this.retrieverRouter = retrieverRouter;
        this.rerankerService = rerankerService;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.confidenceScorer = confidenceScorer;
        this.safetyChecker = safetyChecker;
        this.piiRedactor = piiRedactor;
        this.metricsCollector = metricsCollector;
        this.answerCache = answerCache;
        this.modelRouter = modelRouter;
        this.qualityEvaluator = qualityEvaluator;
        this.topK = topK;
        this.finalK = finalK;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public ChatResponse chat(ChatRequest request) throws IOException {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().substring(0, 12);
        MDC.put("traceId", traceId);
        MDC.put("userId", request.userId());
        MDC.put("sessionId", request.sessionId());

        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();

        try {
            // 1. Get conversation history
            List<ConversationTurn> history = conversationManager.getHistory(sessionId);

            // 2. Rewrite query for multi-turn context
            String standaloneQuery = queryRewriter.rewrite(request.query(), history);
            MDC.put("rewrittenQuery", standaloneQuery);

            // 2.5. Calculate query complexity for model routing
            double queryComplexity = modelRouter.calculateComplexity(standaloneQuery);
            MDC.put("queryComplexity", String.valueOf(queryComplexity));

            // 3. Cache check
            String cacheKey = standaloneQuery.toLowerCase().strip();
            ChatResponse cached = answerCache.getIfPresent(cacheKey);
            if (cached != null) {
                metricsCollector.recordCacheHit();
                log.info("Cache HIT for query");
                return new ChatResponse(
                    cached.answer(), cached.sources(), sessionId, cached.refused(),
                    cached.refusalReason(), cached.confidenceScore(), traceId,
                    System.currentTimeMillis() - startTime, cached.tokenUsage()
                );
            }
            metricsCollector.recordCacheMiss();

            // 4. Embed the standalone query
            float[] queryVector = embeddingService.embed(standaloneQuery);

            // 5. Retrieve
            List<RetrievedChunk> retrieved = retrieverRouter.retrieve(standaloneQuery, queryVector, topK);
            log.info("Retrieved {} chunks", retrieved.size());

            // 6. Rerank (config-driven, may be pass-through)
            List<RetrievedChunk> reranked = rerankerService.rerank(standaloneQuery, retrieved, finalK);

            // 7. Confidence check
            double confidence = confidenceScorer.score(standaloneQuery, reranked);
            MDC.put("confidenceScore", String.valueOf(confidence));

            // 8. Safety check
            SafetyChecker.SafetyResult safety = safetyChecker.check(request.query(), standaloneQuery, confidence);
            if (safety.refused()) {
                log.info("Request refused: reason={}", safety.reason());
                metricsCollector.recordRefusal();
                metricsCollector.recordLatency(System.currentTimeMillis() - startTime);
                return new ChatResponse(
                    safety.guidanceMessage(), reranked, sessionId, true, safety.reason(),
                    confidence, traceId, System.currentTimeMillis() - startTime, null
                );
            }

            // 9. Build prompt
            PromptBuilder.PromptResult prompt = promptBuilder.build(standaloneQuery, reranked, history);

            // 10. Select model based on query complexity and confidence
            String selectedModel = modelRouter.selectModel(
                standaloneQuery, confidence, queryComplexity, ModelRouter.ModelPurpose.CHAT);
            MDC.put("selectedModel", selectedModel);
            log.info("Selected model: {} (complexity: {}, confidence: {})", 
                selectedModel, String.format("%.2f", queryComplexity), String.format("%.2f", confidence));

            // 11. Generate answer with selected model
            String rawAnswer = llmClient.chatCompletion(
                prompt.systemPrompt(), prompt.messages(), maxTokens, temperature, selectedModel);
            TokenUsage tokenUsage = llmClient.getLastTokenUsage();

            // 11. PII redaction
            String safeAnswer = piiRedactor.redact(rawAnswer);

            // 12. Save to conversation
            conversationManager.addTurn(sessionId, request.query(), safeAnswer);

            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordSuccess(latency, tokenUsage);

            ChatResponse response = new ChatResponse(
                safeAnswer, reranked, sessionId, false, null,
                confidence, traceId, latency, tokenUsage
            );

            // 13. Cache the response
            answerCache.put(cacheKey, response);

            log.info("Chat completed: latency={}ms, tokens={}, confidence={}",
                      latency, tokenUsage != null ? tokenUsage.totalTokens() : 0, String.format("%.3f", confidence));

            return response;

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordError();
            log.error("Chat failed after {}ms: {}", latency, e.getMessage(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}
