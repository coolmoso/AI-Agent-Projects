package org.example.qaagent.ingestion;

import org.example.qaagent.model.SectionChunk;
import org.example.qaagent.util.LanguageDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentProcessor {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final PdfExtractor pdfExtractor;
    private final OcrProcessor ocrProcessor;
    private final TextChunker textChunker;
    private final MarkdownHeaderTextSplitter markdownSplitter;
    private final ChunkIndexer chunkIndexer;
    private final LanguageDetector languageDetector;

    public DocumentProcessor(PdfExtractor pdfExtractor, OcrProcessor ocrProcessor,
                              TextChunker textChunker, MarkdownHeaderTextSplitter markdownSplitter,
                              ChunkIndexer chunkIndexer, LanguageDetector languageDetector) {
        this.pdfExtractor = pdfExtractor;
        this.ocrProcessor = ocrProcessor;
        this.textChunker = textChunker;
        this.markdownSplitter = markdownSplitter;
        this.chunkIndexer = chunkIndexer;
        this.languageDetector = languageDetector;
    }

    public void process(File file) throws IOException {
        String text;
        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".pdf")) {
            text = pdfExtractor.extract(file);
            if (text == null) {
                text = ocrProcessor.ocr(file);
            }
        } else if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
            text = Files.readString(file.toPath());
        } else {
            log.warn("Unsupported file type: {}", fileName);
            return;
        }

        if (text == null || text.isBlank()) {
            log.warn("No text extracted from: {}", fileName);
            return;
        }

        String language = languageDetector.detect(text);
        chunkIndexer.createIndexIfNotExists();

        // Use MarkdownHeaderTextSplitter + RecursiveCharacterTextSplitter pipeline for .md files
        if (fileName.endsWith(".md")) {
            List<SectionChunk> sections = markdownSplitter.split(text);
            List<SectionChunk> finalChunks = new ArrayList<>();
            for (SectionChunk section : sections) {
                List<SectionChunk> sectionChunks = textChunker.chunkWithMetadata(
                    section.content(),
                    section.sectionHeaders()
                );
                finalChunks.addAll(sectionChunks);
            }
            chunkIndexer.indexSectionChunks(finalChunks, file.getName(), language);
            log.info("Processed {}: {} chunks (markdown pipeline), lang={}", file.getName(), finalChunks.size(), language);
        } else {
            // Use plain TextChunker for non-markdown files
            List<String> chunks = textChunker.chunk(text);
            chunkIndexer.indexChunks(chunks, file.getName(), language);
            log.info("Processed {}: {} chunks (standard pipeline), lang={}", file.getName(), chunks.size(), language);
        }
    }
}
