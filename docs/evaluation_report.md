# Evaluation Report

## Aggregate Metrics

| Metric | Before | After | Delta | Target |
|--------|--------|-------|-------|--------|
| Faithfulness | - | - | - | >= 0.85 |
| Context Precision | - | - | - | >= 0.70 |
| Answer Compliance | - | - | - | >= 80% |
| Style Consistency | - | - | - | >= 80% |
| Refusal Appropriateness | - | - | - | >= 80% |

## Retrieval Configuration Comparison

| Config | Faithfulness | Context Precision | Compliance | Style | Refusal |
|--------|-------------|-------------------|------------|-------|---------|
| Vector only | - | - | - | - | - |
| Hybrid (no reranker) | - | - | - | - | - |
| Hybrid + Reranker | - | - | - | - | - |

*Run `scripts/run_eval.sh` (Linux/Mac) or `scripts/run_eval.bat` (Windows) to populate this report.*
