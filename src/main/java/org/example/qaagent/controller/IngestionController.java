package org.example.qaagent.controller;

import org.example.qaagent.ingestion.DocumentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IngestionController {
    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final DocumentProcessor documentProcessor;

    public IngestionController(DocumentProcessor documentProcessor) {
        this.documentProcessor = documentProcessor;
    }

    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            Path tempDir = Files.createTempDirectory("rag-ingest");
            File tempFile = tempDir.resolve(file.getOriginalFilename()).toFile();
            file.transferTo(tempFile);

            documentProcessor.process(tempFile);

            // Cleanup
            tempFile.delete();
            tempDir.toFile().delete();

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "fileName", file.getOriginalFilename(),
                "size", file.getSize()
            ));
        } catch (IOException e) {
            log.error("Ingestion failed for {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Ingestion failed: " + e.getMessage()
            ));
        }
    }
}
