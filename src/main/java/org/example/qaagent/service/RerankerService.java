package org.example.qaagent.service;

import org.example.qaagent.model.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class RerankerService {
    private static final Logger log = LoggerFactory.getLogger(RerankerService.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json");

    private final boolean enabled;
    private final String endpoint;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public RerankerService(
            @Value("${retrieval.reranker.enabled:false}") boolean enabled,
            @Value("${retrieval.reranker.endpoint:http://localhost:8082/rerank}") String endpoint) {
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> chunks, int finalK) throws IOException {
        if (!enabled || chunks.isEmpty()) {
            log.debug("Reranker disabled or no chunks; returning top-{}", finalK);
            return chunks.stream().limit(finalK).collect(Collectors.toList());
        }

        List<String> documents = chunks.stream().map(RetrievedChunk::content).toList();
        Map<String, Object> body = Map.of(
            "query", query,
            "texts", documents,
            "truncate", true
        );

        Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Reranker API failed ({}), falling back to original order", response.code());
                return chunks.stream().limit(finalK).collect(Collectors.toList());
            }

            JsonNode root = mapper.readTree(response.body().string());
            List<RetrievedChunk> reranked = new ArrayList<>();
            for (JsonNode item : root) {
                int index = item.get("index").asInt();
                double score = item.get("score").asDouble();
                RetrievedChunk original = chunks.get(index);
                reranked.add(new RetrievedChunk(
                    original.content(), original.sourceFile(),
                    original.chunkIndex(), score
                ));
            }
            reranked.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
            log.info("Reranked {} chunks -> top-{}", chunks.size(), finalK);
            return reranked.stream().limit(finalK).collect(Collectors.toList());
        }
    }
}
