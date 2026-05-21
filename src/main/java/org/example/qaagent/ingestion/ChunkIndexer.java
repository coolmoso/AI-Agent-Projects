package org.example.qaagent.ingestion;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import org.example.qaagent.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class ChunkIndexer {
    private static final Logger log = LoggerFactory.getLogger(ChunkIndexer.class);

    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;
    private final String indexName;

    public ChunkIndexer(ElasticsearchClient esClient,
                         EmbeddingService embeddingService,
                         @Value("${elasticsearch.index-name:rag-knowledge}") String indexName) {
        this.esClient = esClient;
        this.embeddingService = embeddingService;
        this.indexName = indexName;
    }

    public void createIndexIfNotExists() throws IOException {
        boolean exists = esClient.indices().exists(e -> e.index(indexName)).value();
        if (exists) return;

        esClient.indices().create(c -> c
            .index(indexName)
            .settings(s -> s
                .analysis(a -> a
                    .analyzer("bilingual", an -> an
                        .custom(cu -> cu
                            .tokenizer("standard")
                            .filter("lowercase", "asciifolding")
                        )
                    )
                )
            )
            .mappings(m -> m
                .properties("content", p -> p
                    .text(t -> t.analyzer("bilingual"))
                )
                .properties("embedding", p -> p
                    .denseVector(dv -> dv
                        .dims(1536)
                        .index(true)
                        .similarity("cosine")
                    )
                )
                .properties("source_file", p -> p.keyword(k -> k))
                .properties("chunk_index", p -> p.integer(i -> i))
                .properties("language", p -> p.keyword(k -> k))
                .properties("created_at", p -> p.date(d -> d))
            )
        );
        log.info("Created ES index: {}", indexName);
    }

    public void indexChunks(List<String> chunks, String sourceFile, String language) throws IOException {
        List<float[]> vectors = embeddingService.embedBatch(chunks);

        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (int i = 0; i < chunks.size(); i++) {
            final int idx = i;
            final float[] vec = vectors.get(i);
            Map<String, Object> doc = new HashMap<>();
            doc.put("content", chunks.get(idx));
            doc.put("embedding", toFloatList(vec));
            doc.put("source_file", sourceFile);
            doc.put("chunk_index", idx);
            doc.put("language", language);
            doc.put("created_at", new Date());

            bulk.operations(op -> op
                .index(ix -> ix
                    .index(indexName)
                    .id(sourceFile + "_chunk_" + idx)
                    .document(doc)
                )
            );
        }

        BulkResponse response = esClient.bulk(bulk.build());
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                if (item.error() != null) {
                    log.error("Bulk index error: {}", item.error().reason());
                }
            }
        }
        log.info("Indexed {} chunks from {} (lang={})", chunks.size(), sourceFile, language);
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
