package com.smartstudy.backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    private static final int MIN_EMBEDDED_TEXT_LENGTH = 100;

    private final MlClient mlClient;

    public PdfTextExtractor(MlClient mlClient) {
        this.mlClient = mlClient;
    }

    public String extractText(Path pdfPath) throws IOException {
        return extractText(Files.readAllBytes(pdfPath));
    }

    public String extractText(byte[] pdfBytes) throws IOException {
        String embeddedText;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            embeddedText = stripper.getText(document);
        }
        if (embeddedText != null && embeddedText.strip().length() >= MIN_EMBEDDED_TEXT_LENGTH) {
            return embeddedText;
        }

        var ocrResult = mlClient.extractPdfText(pdfBytes);
        if (ocrResult == null || ocrResult.text() == null
                || ocrResult.text().strip().length() < MIN_EMBEDDED_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "No readable text could be recognized in this PDF. Try a clearer scan.");
        }
        return ocrResult.text();
    }
}