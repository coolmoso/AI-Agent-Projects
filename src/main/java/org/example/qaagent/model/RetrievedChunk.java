package org.example.qaagent.model;

import java.util.Map;

public record RetrievedChunk(
    String content,
    String sourceFile,
    int chunkIndex,
    double score,
    Map<String, String> sectionHeaders
) {
    /**
     * Backward-compatible constructor for callers that do not have section metadata.
     */
    public RetrievedChunk(String content, String sourceFile, int chunkIndex, double score) {
        this(content, sourceFile, chunkIndex, score, Map.of());
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
