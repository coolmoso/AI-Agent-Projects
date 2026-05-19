# Cost Analysis

## Per-1000 Calls Token Cost Estimate

### Assumptions per request
- Query rewrite: ~200 prompt + 50 completion tokens
- Embedding: ~50 tokens (query only; documents pre-embedded)
- LLM generation: ~2000 prompt tokens + 300 completion tokens
- Cache hit rate: ~20%

### Cost Breakdown

| Component | Tokens/request | Cost/1K tokens | Cost/1000 calls |
|-----------|---------------|----------------|-----------------|
| Query rewrite (input) | 200 | $0.15/1M | $0.024 |
| Query rewrite (output) | 50 | $0.60/1M | $0.024 |
| Embedding | 50 | $0.02/1M | $0.001 |
| Generation (input) | 2000 | $0.15/1M | $0.240 |
| Generation (output) | 300 | $0.60/1M | $0.144 |
| Reranker (Cohere) | - | $1/1K | $0.800 |
| **Subtotal (no cache)** | | | **$1.233** |
| **With 20% cache hit** | | | **~$0.986** |

### Without Cohere reranker (local BGE-M3): ~$0.186 per 1000 calls

## Model Selection Trade-off

| Model | Cost/1K calls | Quality | Latency | Recommendation |
|-------|--------------|---------|---------|----------------|
| gpt-4o-mini + BGE-M3 | ~$0.19 | Good | ~5s p50 | **Default** |
| gpt-4o-mini + Cohere | ~$0.99 | Better retrieval | ~6s p50 | Budget allows |
| gpt-4o + Cohere | ~$5.50 | Best quality | ~8s p50 | Complex queries only |
