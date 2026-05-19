package org.example.qaagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public EmbeddingService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.embedding-model:text-embedding-3-small}") String model,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public float[] embed(String text) throws IOException {
        return embedBatch(List.of(text)).get(0);
    }

    public List<float[]> embedBatch(List<String> texts) throws IOException {
        String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "input", texts
        ));

        Request request = new Request.Builder()
                .url(baseUrl + "/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Embedding API error: " + response.code() + " " + response.body().string());
            }
            JsonNode root = mapper.readTree(response.body().string());
            JsonNode dataArr = root.get("data");
            List<float[]> results = new ArrayList<>();
            for (JsonNode item : dataArr) {
                JsonNode embedding = item.get("embedding");
                float[] vec = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vec[i] = (float) embedding.get(i).asDouble();
                }
                results.add(vec);
            }
            log.debug("Embedded {} texts, model={}, usage={}",
                       texts.size(), model, root.get("usage"));
            return results;
        }
    }
}
