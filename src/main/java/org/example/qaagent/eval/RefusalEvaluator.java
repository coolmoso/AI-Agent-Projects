package org.example.qaagent.eval;

import org.springframework.stereotype.Component;

@Component
public class RefusalEvaluator {

    /**
     * Evaluate refusal appropriateness.
     * Two sub-evaluations:
     * A. Should-refuse-did-refuse (true positive)
     * B. Should-answer-did-answer (true negative)
     * Score = (TP + TN) / (TP + TN + FP + FN)
     */
    public double evaluate(int truePositives, int trueNegatives, int falsePositives, int falseNegatives) {
        int total = truePositives + trueNegatives + falsePositives + falseNegatives;
        if (total == 0) return 0.0;
        return (double)(truePositives + trueNegatives) / total;
    }
}
