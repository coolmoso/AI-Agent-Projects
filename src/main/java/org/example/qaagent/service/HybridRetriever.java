package org.example.qaagent.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.example.qaagent.model.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class HybridRetriever {
    private final ElasticsearchClient esClient;
    private final String indexName;
    private final float vectorWeight;
    private final float bm25Weight;

    public HybridRetriever(ElasticsearchClient esClient,
                            @Value("${elasticsearch.index-name:rag-knowledge}") String indexName,
                            @Value("${retrieval.hybrid.vector-weight:0.7}") float vectorWeight,
                            @Value("${retrieval.hybrid.bm25-weight:0.3}") float bm25Weight) {
        this.esClient = esClient;
        this.indexName = indexName;
        this.vectorWeight = vectorWeight;
        this.bm25Weight = bm25Weight;
    }

    @SuppressWarnings("unchecked")
    public List<RetrievedChunk> search(String query, float[] queryVector, int topK) throws IOException {
        int candidates = topK * 3;

        SearchResponse<Map> vecResponse = esClient.search(s -> s
            .index(indexName)
            .knn(k -> k
                .field("embedding")
                .queryVector(toFloatList(queryVector))
                .k((long) candidates)
                .numCandidates((long) candidates * 5)
            )
            .size(candidates),
            Map.class
        );

        SearchResponse<Map> bm25Response = esClient.search(s -> s
            .index(indexName)
            .query(q -> q
                .match(m -> m
                    .field("content")
                    .query(query)
                )
            )
            .size(candidates),
            Map.class
        );

        Map<String, RrfEntry> fusionMap = new LinkedHashMap<>();
        int rank = 1;
        for (Hit<Map> hit : vecResponse.hits().hits()) {
            String id = hit.id();
            fusionMap.computeIfAbsent(id, k -> new RrfEntry(hit.source()));
            fusionMap.get(id).rrfScore += vectorWeight * (1.0 / (60 + rank));
            rank++;
        }
        rank = 1;
        for (Hit<Map> hit : bm25Response.hits().hits()) {
            String id = hit.id();
            fusionMap.computeIfAbsent(id, k -> new RrfEntry(hit.source()));
            fusionMap.get(id).rrfScore += bm25Weight * (1.0 / (60 + rank));
            rank++;
        }

        return fusionMap.values().stream()
            .sorted(Comparator.comparingDouble((RrfEntry e) -> e.rrfScore).reversed())
            .limit(topK)
            .map(e -> new RetrievedChunk(
                (String) e.source.get("content"),
                (String) e.source.get("source_file"),
                e.source.get("chunk_index") instanceof Integer ? (Integer) e.source.get("chunk_index") : 0,
                e.rrfScore
            ))
            .toList();
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }

    private static class RrfEntry {
        Map<String, Object> source;
        double rrfScore = 0.0;
        RrfEntry(Map<String, Object> source) { this.source = source; }
    }
}
