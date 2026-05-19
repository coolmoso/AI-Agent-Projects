# Issue Diagnosis Playbook

## Issue 1: Compliance Drop After Corpus Update

**Symptom**: Answer compliance dropped from 88% to 72% after ingesting new technical documents.

**Log/Metric Evidence**:
- Metrics report shows compliance drop: `{"metric": "answer_compliance", "before": 0.88, "after": 0.72}`
- Structured logs show low confidence for new-topic queries
- Context precision dropped: 0.78 -> 0.55

**Root Cause Analysis**:
1. New documents were ingested with default chunk size (512), but technical specs had long tables split mid-content
2. Bilingual analyzer was not tokenizing new Chinese technical terms correctly

**Fix**:
1. Increased `chunking.size` to 768 for technical documents
2. Added custom Chinese technical term dictionary to ICU analyzer
3. Re-ingested the new documents

**Post-Fix**: Compliance 72% -> 86% (+14%), Context Precision 0.55 -> 0.75 (+36%)

---

## Issue 2: Refusal Spike After Threshold Tuning

**Symptom**: Refusal rate jumped from 5% to 25% after adjusting `safety.confidence-threshold` from 0.30 to 0.50.

**Log/Metric Evidence**:
- Refusal rate: 0.05 -> 0.25
- Legitimate queries being refused with confidence scores 0.35-0.50

**Root Cause Analysis**:
1. Confidence threshold set too aggressively at 0.50
2. Bilingual queries naturally score lower due to cross-language embedding similarity

**Fix**:
1. Lowered threshold to 0.35
2. Added language-aware confidence normalization (+0.05 boost for ZH queries)

**Post-Fix**: Refusal rate 25% -> 7% (-72%), Refusal appropriateness 65% -> 88% (+23%)
