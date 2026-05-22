package org.example.qaagent.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DirectoryIngestor implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DirectoryIngestor.class);

    private final DocumentProcessor documentProcessor;
    private final String docsDirectory;

    public DirectoryIngestor(DocumentProcessor documentProcessor,
                            @Value("${ingestion.docs-directory:docs}") String docsDirectory) {
        this.documentProcessor = documentProcessor;
        this.docsDirectory = docsDirectory;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Ingesting documents from directory: {}", docsDirectory);
        Path docsPath = Paths.get(docsDirectory);
        
        if (!Files.exists(docsPath)) {
            log.warn("Docs directory does not exist: {}", docsPath.toAbsolutePath());
            return;
        }

        if (!Files.isDirectory(docsPath)) {
            log.warn("Docs path is not a directory: {}", docsPath.toAbsolutePath());
            return;
        }

        log.info("Scanning docs directory: {}", docsPath.toAbsolutePath());
        
        List<File> pdfFiles = Arrays.stream(docsPath.toFile().listFiles())
            .filter(file -> file.isFile() && file.getName().toLowerCase().endsWith(".pdf"))
            .collect(Collectors.toList());

        if (pdfFiles.isEmpty()) {
            log.info("No PDF files found in docs directory");
            return;
        }

        log.info("Found {} PDF files to ingest", pdfFiles.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (File pdfFile : pdfFiles) {
            try {
                log.info("Processing: {}", pdfFile.getName());
                documentProcessor.process(pdfFile);
                successCount++;
                log.info("Successfully processed: {}", pdfFile.getName());
            } catch (IOException e) {
                failureCount++;
                log.error("Failed to process {}: {}", pdfFile.getName(), e.getMessage(), e);
            }
        }
        
        log.info("Directory ingestion complete: {} succeeded, {} failed", successCount, failureCount);
    }
}
