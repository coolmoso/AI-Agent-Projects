package org.example.qaagent.ingestion;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class OcrProcessor {
    private static final Logger log = LoggerFactory.getLogger(OcrProcessor.class);
    private final ITesseract tesseract;

    public OcrProcessor(@Value("${ocr.tessdata-path:/usr/share/tesseract-ocr/5/tessdata}") String tessDataPath,
                         @Value("${ocr.language:eng+chi_sim}") String language) {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tessDataPath);
        this.tesseract.setLanguage(language);
        this.tesseract.setPageSegMode(1);
    }

    public String ocr(File pdfFile) {
        try {
            String text = tesseract.doOCR(pdfFile);
            log.info("OCR completed for {}: {} chars extracted", pdfFile.getName(), text.length());
            return text.strip();
        } catch (TesseractException e) {
            log.error("OCR failed for {}: {}", pdfFile.getName(), e.getMessage());
            throw new RuntimeException("OCR failed", e);
        }
    }
}
