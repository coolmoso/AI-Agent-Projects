#!/usr/bin/env bash
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
OUTPUT_DIR="${OUTPUT_DIR:-reports}"

mkdir -p "$OUTPUT_DIR"

echo "Fetching operations report..."
curl -sf "$APP_URL/api/v1/report" -o "$OUTPUT_DIR/report.json"
curl -sf "$APP_URL/api/v1/report/csv" -o "$OUTPUT_DIR/report.csv"
echo "Reports saved to $OUTPUT_DIR/"
