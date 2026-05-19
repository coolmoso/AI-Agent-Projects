package org.example.qaagent.service;

import org.example.qaagent.model.ConversationTurn;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationManager {

    private final Cache<String, List<ConversationTurn>> conversations;
    private final int maxTurns;

    public ConversationManager(
            @Value("${conversation.max-turns:10}") int maxTurns,
            @Value("${conversation.ttl-minutes:30}") int ttlMinutes) {
        this.maxTurns = maxTurns;
        this.conversations = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(ttlMinutes))
                .build();
    }

    public List<ConversationTurn> getHistory(String sessionId) {
        return conversations.getIfPresent(sessionId);
    }

    public void addTurn(String sessionId, String userMessage, String assistantMessage) {
        List<ConversationTurn> history = conversations.get(sessionId, k -> new ArrayList<>());
        history.add(new ConversationTurn("user", userMessage));
        history.add(new ConversationTurn("assistant", assistantMessage));
        while (history.size() > maxTurns * 2) {
            history.remove(0);
            history.remove(0);
        }
    }
}
