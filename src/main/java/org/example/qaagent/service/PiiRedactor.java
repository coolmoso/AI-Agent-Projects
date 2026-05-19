package org.example.qaagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PiiRedactor {
    private static final Logger log = LoggerFactory.getLogger(PiiRedactor.class);

    private static final Map<String, Pattern> PII_PATTERNS = new LinkedHashMap<>();
    static {
        PII_PATTERNS.put("[EMAIL]",
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"));
        PII_PATTERNS.put("[PHONE]",
            Pattern.compile("(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}"));
        PII_PATTERNS.put("[ID_CARD]",
            Pattern.compile("\\d{17}[\\dXx]"));
        PII_PATTERNS.put("[SSN]",
            Pattern.compile("\\d{3}-\\d{2}-\\d{4}"));
        PII_PATTERNS.put("[CREDIT_CARD]",
            Pattern.compile("\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}[-.\\s]?\\d{4}"));
        PII_PATTERNS.put("[IP_ADDR]",
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"));
    }

    public String redact(String text) {
        if (text == null) return null;
        String redacted = text;
        boolean changed = false;
        for (Map.Entry<String, Pattern> entry : PII_PATTERNS.entrySet()) {
            String replacement = entry.getKey();
            Pattern pattern = entry.getValue();
            String before = redacted;
            redacted = pattern.matcher(redacted).replaceAll(replacement);
            if (!before.equals(redacted)) changed = true;
        }
        if (changed) {
            log.info("PII redacted from output");
        }
        return redacted;
    }
}
