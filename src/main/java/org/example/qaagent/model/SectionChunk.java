package org.example.qaagent.model;

import java.util.Map;

/**
 * Represents a text chunk with section-level metadata preserved from
 * the MarkdownHeaderTextSplitter pipeline. Each chunk carries its
 * hierarchical header context (e.g., Header 1 > Header 2 > Header 3).
 */
public record SectionChunk(
    String content,
    Map<String, String> sectionHeaders
) {
    /**
     * Convenience constructor for plain text without section metadata.
     */
    public SectionChunk(String content) {
        this(content, Map.of());
    }

    /**
     * Returns a human-readable section path, e.g. "Overview > Configuration > S3 Settings".
     */
    public String sectionPath() {
        if (sectionHeaders == null || sectionHeaders.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int level = 1; level <= 6; level++) {
            String key = "Header " + level;
            if (sectionHeaders.containsKey(key)) {
                if (sb.length() > 0) sb.append(" > ");
                sb.append(sectionHeaders.get(key));
            }
        }
        return sb.toString();
    }
}
