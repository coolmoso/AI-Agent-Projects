package org.example.qaagent.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private final int chunkSize;
    private final int chunkOverlap;
    private static final String[] SEPARATORS = {"\n\n", "\n", "\u3002", ". ", "\uff1b", "; ", "\uff0c", ", ", " ", ""};

    public TextChunker(@Value("${chunking.size:512}") int chunkSize,
                       @Value("${chunking.overlap:64}") int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<String> chunk(String text) {
        List<String> rawChunks = splitRecursive(text, 0);
        return applyOverlap(rawChunks);
    }

    private List<String> splitRecursive(String text, int separatorIndex) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        if (separatorIndex >= SEPARATORS.length) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < text.length(); i += chunkSize) {
                result.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
            return result;
        }

        String sep = SEPARATORS[separatorIndex];
        String[] parts = text.split(sep.isEmpty() ? "(?<=.)" : java.util.regex.Pattern.quote(sep));
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            if (current.length() + part.length() + sep.length() > chunkSize && current.length() > 0) {
                result.add(current.toString().strip());
                current = new StringBuilder();
            }
            if (part.length() > chunkSize) {
                if (current.length() > 0) {
                    result.add(current.toString().strip());
                    current = new StringBuilder();
                }
                result.addAll(splitRecursive(part, separatorIndex + 1));
            } else {
                if (current.length() > 0) current.append(sep);
                current.append(part);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().strip());
        }
        return result;
    }

    private List<String> applyOverlap(List<String> chunks) {
        if (chunkOverlap <= 0 || chunks.size() <= 1) return chunks;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (i > 0) {
                String prev = chunks.get(i - 1);
                String overlapText = prev.substring(Math.max(0, prev.length() - chunkOverlap));
                chunk = overlapText + " " + chunk;
            }
            result.add(chunk);
        }
        return result;
    }
}
