package org.example.qaagent.service;

import org.example.qaagent.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    private volatile TokenUsage lastTokenUsage;

    public LlmClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.chat-model:gpt-4o-mini}") String model,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String chatCompletion(String systemPrompt, List<Map<String, String>> messages,
                                  int maxTokens, double temperature) throws IOException {
        return chatCompletion(systemPrompt, messages, maxTokens, temperature, model);
    }

    public String chatCompletion(String systemPrompt, List<Map<String, String>> messages,
                                  int maxTokens, double temperature, String modelOverride) throws IOException {
        var allMessages = new java.util.ArrayList<Map<String, String>>();
        allMessages.add(Map.of("role", "system", "content", systemPrompt));
        allMessages.addAll(messages);

        Map<String, Object> body = Map.of(
            "model", modelOverride != null ? modelOverride : model,
            "messages", allMessages,
            "max_tokens", maxTokens,
            "temperature", temperature
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "no body";
                throw new IOException("LLM API error: " + response.code() + " " + errBody);
            }
            JsonNode root = mapper.readTree(response.body().string());
            JsonNode usage = root.get("usage");
            if (usage != null) {
                this.lastTokenUsage = new TokenUsage(
                    usage.get("prompt_tokens").asInt(),
                    usage.get("completion_tokens").asInt(),
                    usage.get("total_tokens").asInt()
                );
            }
            return root.get("choices").get(0).get("message").get("content").asText();
        }
    }

    public String complete(String prompt, int maxTokens, double temperature) throws IOException {
        return chatCompletion("You are a helpful assistant.",
            List.of(Map.of("role", "user", "content", prompt)), maxTokens, temperature);
    }

    public String complete(String prompt, int maxTokens, double temperature, String modelOverride) throws IOException {
        return chatCompletion("You are a helpful assistant.",
            List.of(Map.of("role", "user", "content", prompt)), maxTokens, temperature, modelOverride);
    }

    public TokenUsage getLastTokenUsage() { return lastTokenUsage; }
}
