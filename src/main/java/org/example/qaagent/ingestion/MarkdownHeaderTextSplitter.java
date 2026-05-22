package org.example.qaagent.ingestion;

import org.example.qaagent.model.SectionChunk;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits Markdown documents by header boundaries, mirroring LangChain's
 * MarkdownHeaderTextSplitter. Each resulting section carries hierarchical
 * metadata (Header 1 through Header 6) so downstream chunks preserve
 * their location within the document structure.
 *
 * <p>Headers at level N reset all tracked headers at levels > N, maintaining
 * a clean breadcrumb trail for each section.</p>
 */
@Component
public class MarkdownHeaderTextSplitter {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private static final List<HeaderType> HEADERS_TO_SPLIT_ON = List.of(
        new HeaderType("#", "Header 1"),
        new HeaderType("##", "Header 2"),
        new HeaderType("###", "Header 3"),
        new HeaderType("####", "Header 4"),
        new HeaderType("#####", "Header 5"),
        new HeaderType("######", "Header 6")
    );

    /**
     * Split markdown text into sections preserving header hierarchy as metadata.
     *
     * @param markdownText raw markdown content
     * @return list of SectionChunk, each with content and section header metadata
     */
    public List<SectionChunk> split(String markdownText) {
        if (markdownText == null || markdownText.isBlank()) {
            return List.of();
        }

        String[] lines = markdownText.split("\\r?\\n");
        List<SectionChunk> sections = new ArrayList<>();
        Map<String, String> currentHeaders = new LinkedHashMap<>();
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = HEADER_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                // Flush accumulated content as a section
                if (currentContent.length() > 0) {
                    String text = currentContent.toString().strip();
                    if (!text.isEmpty()) {
                        sections.add(new SectionChunk(text, new LinkedHashMap<>(currentHeaders)));
                    }
                    currentContent.setLength(0);
                }

                // Determine header level and update hierarchy
                String hashes = matcher.group(1);
                String headerText = matcher.group(2).strip();
                int level = hashes.length();

                // Find the metadata key for this header level
                String metadataKey = null;
                for (HeaderType ht : HEADERS_TO_SPLIT_ON) {
                    if (ht.marker().length() == level) {
                        metadataKey = ht.name();
                        break;
                    }
                }

                if (metadataKey != null) {
                    // Set this header level
                    currentHeaders.put(metadataKey, headerText);

                    // Clear all deeper header levels
                    for (int deeper = level + 1; deeper <= 6; deeper++) {
                        currentHeaders.remove("Header " + deeper);
                    }
                }
            } else {
                if (currentContent.length() > 0) {
                    currentContent.append("\n");
                }
                currentContent.append(line);
            }
        }

        // Flush remaining content
        if (currentContent.length() > 0) {
            String text = currentContent.toString().strip();
            if (!text.isEmpty()) {
                sections.add(new SectionChunk(text, new LinkedHashMap<>(currentHeaders)));
            }
        }

        return sections;
    }

    private record HeaderType(String marker, String name) {}
}
