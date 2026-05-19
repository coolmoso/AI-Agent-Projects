package org.example.qaagent.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.qaagent.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, ChatResponse> answerCache(
            @Value("${cache.answer.max-size:5000}") int maxSize,
            @Value("${cache.answer.ttl-minutes:60}") int ttlMinutes) {
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .recordStats()
                .build();
    }
}
