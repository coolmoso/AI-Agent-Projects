package org.example.qaagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SafetyChecker {

    private final double confidenceThreshold;
    private final List<Pattern> injectionPatterns;

    public SafetyChecker(@Value("${safety.confidence-threshold:0.35}") double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
        this.injectionPatterns = List.of(
            Pattern.compile("(?i)ignore (all )?(previous|above|prior) (instructions|prompts|rules)"),
            Pattern.compile("(?i)you are now"),
            Pattern.compile("(?i)disregard (your|the) (instructions|system prompt)"),
            Pattern.compile("(?i)forget (everything|your instructions)"),
            Pattern.compile("(?i)pretend (you are|to be)"),
            Pattern.compile("(?i)system prompt"),
            Pattern.compile("(?i)\\bDAN\\b"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("\u5ffd\u7565(\u4e4b\u524d|\u4e0a\u9762|\u6240\u6709)(\u7684)?(\u6307\u4ee4|\u63d0\u793a|\u89c4\u5219)"),
            Pattern.compile("\u4f60\u73b0\u5728(\u662f|\u626e\u6f14)"),
            Pattern.compile("\u5fd8\u8bb0(\u4f60\u7684\u6307\u4ee4|\u4e00\u5207)")
        );
    }

    public SafetyResult check(String originalQuery, String rewrittenQuery, double confidence) {
        for (Pattern p : injectionPatterns) {
            if (p.matcher(originalQuery).find()) {
                return SafetyResult.refused("prompt_injection",
                    "I'm unable to process this request. Please rephrase your question about " +
                    "the internal knowledge base.");
            }
        }

        if (confidence < confidenceThreshold) {
            return SafetyResult.refused("low_confidence",
                "I cannot find sufficient information in the knowledge base to answer this question. " +
                "The retrieved documents do not appear relevant enough (confidence: " +
                String.format("%.2f", confidence) + "). " +
                "Please try rephrasing or contact the relevant department.");
        }

        return SafetyResult.allowed();
    }

    public record SafetyResult(boolean refused, String reason, String guidanceMessage) {
        static SafetyResult refused(String reason, String guidance) {
            return new SafetyResult(true, reason, guidance);
        }
        static SafetyResult allowed() {
            return new SafetyResult(false, null, null);
        }
    }
}
