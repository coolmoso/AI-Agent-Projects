package org.example.qaagent.service;

import org.example.qaagent.model.ConversationTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class QueryRewriter {
    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);
    private final LlmClient llmClient;
    private final ModelRouter modelRouter;

    private static final String REWRITE_PROMPT = """
        Given the following conversation history and a follow-up question, rewrite the follow-up
        question as a standalone question that captures all necessary context.

        Rules:
        - Preserve the original language (English or Chinese)
        - Include specific entities, dates, or terms from the conversation
        - If the follow-up is already standalone, return it unchanged
        - Output ONLY the rewritten question, nothing else

        Conversation history:
        %s

        Follow-up question: %s

        Standalone question:""";

    public QueryRewriter(LlmClient llmClient, ModelRouter modelRouter) {
        this.llmClient = llmClient;
        this.modelRouter = modelRouter;
    }

    public String rewrite(String query, List<ConversationTurn> history) throws IOException {
        if (history == null || history.isEmpty()) {
            return query;
        }

        StringBuilder historyStr = new StringBuilder();
        for (ConversationTurn turn : history) {
            historyStr.append(turn.role()).append(": ").append(turn.content()).append("\n");
        }

        String prompt = String.format(REWRITE_PROMPT, historyStr.toString(), query);
        
        // Use fallback model for query rewriting (faster, less critical for quality)
        String rewriteModel = modelRouter.selectModel(query, 1.0, 0.3, ModelRouter.ModelPurpose.REWRITING);
        String rewritten = llmClient.complete(prompt, 150, 0.0, rewriteModel);
        
        log.info("Query rewritten using model '{}': '{}' -> '{}'", rewriteModel, query, rewritten.strip());
        return rewritten.strip();
    }
}
