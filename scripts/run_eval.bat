@echo off
setlocal enabledelayedexpansion
echo ===== RAG QA Service - Full Evaluation Suite =====

set APP_URL=http://localhost:8080
set EVAL_DATASET=src\main\resources\eval\eval_dataset.jsonl
set OUTPUT_DIR=eval-results

mkdir "%OUTPUT_DIR%" 2>nul

echo [1/3] Running vector-only evaluation...
mkdir "%OUTPUT_DIR%\vector" 2>nul
java -jar build\libs\QA-Agent-1.0.0.jar ^
    --spring.profiles.active=eval ^
    --retrieval.mode=vector ^
    --retrieval.reranker.enabled=false ^
    --eval.dataset=%EVAL_DATASET% ^
    --eval.output-dir=%OUTPUT_DIR%\vector

echo [2/3] Running hybrid evaluation...
mkdir "%OUTPUT_DIR%\hybrid" 2>nul
java -jar build\libs\QA-Agent-1.0.0.jar ^
    --spring.profiles.active=eval ^
    --retrieval.mode=hybrid ^
    --retrieval.reranker.enabled=false ^
    --eval.dataset=%EVAL_DATASET% ^
    --eval.output-dir=%OUTPUT_DIR%\hybrid

echo [3/3] Running hybrid+reranker evaluation...
mkdir "%OUTPUT_DIR%\hybrid_reranker" 2>nul
java -jar build\libs\QA-Agent-1.0.0.jar ^
    --spring.profiles.active=eval ^
    --retrieval.mode=hybrid ^
    --retrieval.reranker.enabled=true ^
    --eval.dataset=%EVAL_DATASET% ^
    --eval.output-dir=%OUTPUT_DIR%\hybrid_reranker

echo ===== Done =====
echo Results in: %OUTPUT_DIR%
