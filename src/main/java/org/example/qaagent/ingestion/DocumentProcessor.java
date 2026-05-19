package org.example.qaagent.ingestion;

import org.example.qaagent.util.LanguageDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Service
public class DocumentProcessor {
    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final PdfExtractor pdfExtractor;
    private final OcrProcessor ocrProcessor;
    private final TextChunker textChunker;
    private final ChunkIndexer chunkIndexer;
    private final LanguageDetector languageDetector;

    public DocumentProcessor(PdfExtractor pdfExtractor, OcrProcessor ocrProcessor,
                              TextChunker textChunker, ChunkIndexer chunkIndexer,
                              LanguageDetector languageDetector) {
        this.pdfExtractor = pdfExtractor;
        this.ocrProcessor = ocrProcessor;
        this.textChunker = textChunker;
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
        List<String> chunks = textChunker.chunk(text);
        chunkIndexer.createIndexIfNotExists();
        chunkIndexer.indexChunks(chunks, file.getName(), language);
        log.info("Processed {}: {} chunks, lang={}", file.getName(), chunks.size(), language);
    }
}
