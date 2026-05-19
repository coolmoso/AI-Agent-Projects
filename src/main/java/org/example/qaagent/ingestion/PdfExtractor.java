package org.example.qaagent.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class PdfExtractor {
    private static final Logger log = LoggerFactory.getLogger(PdfExtractor.class);
    private static final int MIN_TEXT_LENGTH = 50;

    public String extract(File pdfFile) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.strip().length() < MIN_TEXT_LENGTH) {
                log.info("PDF appears scanned (text length={}), needs OCR: {}",
                         text == null ? 0 : text.strip().length(), pdfFile.getName());
                return null;
            }
            return text.strip();
        }
    }
}
