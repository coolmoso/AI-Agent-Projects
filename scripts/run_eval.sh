#!/usr/bin/env bash
set -euo pipefail

echo "===== RAG QA Service - Full Evaluation Suite ====="
echo ""

APP_URL="${APP_URL:-http://localhost:8080}"
EVAL_DATASET="${EVAL_DATASET:-src/main/resources/eval/eval_dataset.jsonl}"
OUTPUT_DIR="${OUTPUT_DIR:-eval-results}"
CONFIGS=("vector" "hybrid" "hybrid_reranker")

mkdir -p "$OUTPUT_DIR"

echo "[1/5] Checking service health..."
curl -sf "$APP_URL/actuator/health" > /dev/null || { echo "Service not running at $APP_URL"; exit 1; }
echo "  Service is healthy."

for config in "${CONFIGS[@]}"; do
    echo ""
    echo "[2/5] Running evaluation: $config"

    case "$config" in
        "vector")
            export RETRIEVAL_MODE="vector"
            export RERANKER_ENABLED="false"
            ;;
        "hybrid")
            export RETRIEVAL_MODE="hybrid"
            export RERANKER_ENABLED="false"
            ;;
        "hybrid_reranker")
            export RETRIEVAL_MODE="hybrid"
            export RERANKER_ENABLED="true"
            ;;
    esac

    CONFIG_DIR="$OUTPUT_DIR/$config"
    mkdir -p "$CONFIG_DIR"

    java -jar build/libs/QA-Agent-1.0.0.jar \
        --spring.profiles.active=eval \
        --retrieval.mode="$RETRIEVAL_MODE" \
        --retrieval.reranker.enabled="$RERANKER_ENABLED" \
        --eval.dataset="$EVAL_DATASET" \
        --eval.output-dir="$CONFIG_DIR" \
        2>&1 | tee "$CONFIG_DIR/eval.log"

    echo "  Results saved to $CONFIG_DIR/"
done

echo ""
echo "[3/5] Fetching operations report..."
curl -sf "$APP_URL/api/v1/report/csv" > "$OUTPUT_DIR/operations_report.csv"
echo "  Saved to $OUTPUT_DIR/operations_report.csv"

echo ""
echo "[4/5] Generating comparison report..."
cat > "$OUTPUT_DIR/comparison_report.md" << 'REPORT_HEADER'
# Retrieval Configuration Comparison

| Config | Faithfulness | Context Precision | Compliance | Style | Refusal Accuracy | P50 (ms) | P95 (ms) |
|--------|-------------|-------------------|------------|-------|-----------------|----------|----------|
REPORT_HEADER

for config in "${CONFIGS[@]}"; do
    if [ -f "$OUTPUT_DIR/$config/evaluation_report.md" ]; then
        faith=$(grep "Faithfulness" "$OUTPUT_DIR/$config/evaluation_report.md" | grep -oP '\d+\.\d+' | head -1)
        prec=$(grep "Context Precision" "$OUTPUT_DIR/$config/evaluation_report.md" | grep -oP '\d+\.\d+' | head -1)
        comp=$(grep "Answer Compliance" "$OUTPUT_DIR/$config/evaluation_report.md" | grep -oP '\d+\.\d+' | head -1)
        style=$(grep "Style Consistency" "$OUTPUT_DIR/$config/evaluation_report.md" | grep -oP '\d+\.\d+' | head -1)
        refusal=$(grep "Refusal Appropriateness" "$OUTPUT_DIR/$config/evaluation_report.md" | grep -oP '\d+\.\d+' | head -1)
        echo "| $config | $faith | $prec | $comp% | $style% | $refusal% | - | - |" >> "$OUTPUT_DIR/comparison_report.md"
    fi
done

echo ""
echo "[5/5] Evaluation complete!"
echo ""
echo "Output files:"
echo "  $OUTPUT_DIR/vector/evaluation_report.md"
echo "  $OUTPUT_DIR/hybrid/evaluation_report.md"
echo "  $OUTPUT_DIR/hybrid_reranker/evaluation_report.md"
echo "  $OUTPUT_DIR/operations_report.csv"
echo "  $OUTPUT_DIR/comparison_report.md"
echo ""
echo "===== Done ====="
