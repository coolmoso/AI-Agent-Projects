package org.example.qaagent.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.example.qaagent.model.RetrievedChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class VectorRetriever {
    private final ElasticsearchClient esClient;
    private final String indexName;

    public VectorRetriever(ElasticsearchClient esClient,
                            @Value("${elasticsearch.index-name:rag-knowledge}") String indexName) {
        this.esClient = esClient;
        this.indexName = indexName;
    }

    @SuppressWarnings("unchecked")
    public List<RetrievedChunk> search(float[] queryVector, int topK) throws IOException {
        SearchResponse<Map> response = esClient.search(s -> s
            .index(indexName)
            .knn(k -> k
                .field("embedding")
                .queryVector(toFloatList(queryVector))
                .k((long) topK)
                .numCandidates((long) topK * 10)
            )
            .size(topK),
            Map.class
        );

        List<RetrievedChunk> results = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source();
            Map<String, String> sectionHeaders = extractSectionHeaders(source);
            results.add(new RetrievedChunk(
                (String) source.get("content"),
                (String) source.get("source_file"),
                source.get("chunk_index") instanceof Integer ? (Integer) source.get("chunk_index") : 0,
                hit.score(),
                sectionHeaders
            ));
        }
        return results;
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
}
