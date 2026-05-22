package org.example.qaagent.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import org.example.qaagent.model.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * EnsembleRetriever combines semantic (vector) search and BM25 keyword search
 * with weighted scoring (60% semantic, 40% BM25) and exact-match boosting.
 */
@Component
public class EnsembleRetriever {
    private final ElasticsearchClient esClient;
    private final String indexName;
    private final float semanticWeight;
    private final float bm25Weight;

    public EnsembleRetriever(ElasticsearchClient esClient,
                            @Value("${elasticsearch.index-name:rag-knowledge}") String indexName,
                            @Value("${retrieval.ensemble.semantic-weight:0.6}") float semanticWeight,
                            @Value("${retrieval.ensemble.bm25-weight:0.4}") float bm25Weight) {
        this.esClient = esClient;
        this.indexName = indexName;
        this.semanticWeight = semanticWeight;
        this.bm25Weight = bm25Weight;
    }

    @SuppressWarnings("unchecked")
    public List<RetrievedChunk> search(String query, float[] queryVector, int topK) throws IOException {
        int candidates = topK * 3;

        // Semantic search with KNN
        SearchResponse<Map> semanticResponse = esClient.search(s -> s
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

        // BM25 search with exact-match boosting
        SearchResponse<Map> bm25Response = esClient.search(s -> s
            .index(indexName)
            .query(q -> q
                .bool(b -> b
                    .should(sh -> sh
                        .match(m -> m
                            .field("content")
                            .query(query)
                            .boost(1.0)
                        )
                    )
                    .should(sh -> sh
                        .matchPhrase(m -> m
                            .field("content.exact_match")
                            .query(query)
                            .boost(2.0)
                        )
                    )
                )
            )
            .size(candidates),
            Map.class
        );

        // Combine scores using weighted ensemble
        Map<String, EnsembleEntry> fusionMap = new LinkedHashMap<>();
        
        // Process semantic results
        int rank = 1;
        for (Hit<Map> hit : semanticResponse.hits().hits()) {
            String id = hit.id();
            fusionMap.computeIfAbsent(id, k -> new EnsembleEntry(hit.source()));
            fusionMap.get(id).semanticScore = hit.score() != null ? hit.score() : 0.0;
            fusionMap.get(id).semanticRank = rank++;
        }

        // Process BM25 results
        rank = 1;
        for (Hit<Map> hit : bm25Response.hits().hits()) {
            String id = hit.id();
            fusionMap.computeIfAbsent(id, k -> new EnsembleEntry(hit.source()));
            fusionMap.get(id).bm25Score = hit.score() != null ? hit.score() : 0.0;
            fusionMap.get(id).bm25Rank = rank++;
        }

        // Calculate ensemble scores
        return fusionMap.values().stream()
            .peek(e -> {
                // Normalize scores by rank (reciprocal rank fusion style)
                double normalizedSemantic = e.semanticScore / (60.0 + e.semanticRank);
                double normalizedBm25 = e.bm25Score / (60.0 + e.bm25Rank);
                e.ensembleScore = (semanticWeight * normalizedSemantic) + (bm25Weight * normalizedBm25);
            })
            .sorted(Comparator.comparingDouble((EnsembleEntry e) -> e.ensembleScore).reversed())
            .limit(topK)
            .map(e -> {
                Map<String, String> sectionHeaders = extractSectionHeaders(e.source);
                return new RetrievedChunk(
                    (String) e.source.get("content"),
                    (String) e.source.get("source_file"),
                    e.source.get("chunk_index") instanceof Integer ? (Integer) e.source.get("chunk_index") : 0,
                    e.ensembleScore,
                    sectionHeaders
                );
            })
            .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractSectionHeaders(Map<String, Object> source) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (source.containsKey("section_headers")) {
            Object headersObj = source.get("section_headers");
            if (headersObj instanceof Map) {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) headersObj).entrySet()) {
                    if (entry.getValue() instanceof String) {
                        headers.put(entry.getKey(), (String) entry.getValue());
                    }
                }
            }
        }
        return headers;
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }

    private static class EnsembleEntry {
        Map<String, Object> source;
        double semanticScore = 0.0;
        double bm25Score = 0.0;
        int semanticRank = 0;
        int bm25Rank = 0;
        double ensembleScore = 0.0;

        EnsembleEntry(Map<String, Object> source) {
            this.source = source;
        }
    }
}
