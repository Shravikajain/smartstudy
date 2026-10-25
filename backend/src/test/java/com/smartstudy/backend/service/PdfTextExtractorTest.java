package com.smartstudy.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import com.smartstudy.backend.dto.ml.MlExtractPdfTextResponse;

@SpringBootTest
@ActiveProfiles("test")
class PdfTextExtractorTest {

    @Autowired
    private PdfTextExtractor textExtractor;

    @MockitoBean
    private MlClient mlClient;

    @Test
    void scansPdfWithNoEmbeddedTextUsingMlOcr(@TempDir Path tempDir) throws Exception {
        Path scannedPdf = tempDir.resolve("scan.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(scannedPdf.toFile());
        }
        String recognizedText = "Recognized question text. ".repeat(8);
        when(mlClient.extractPdfText(any(byte[].class)))
                .thenReturn(new MlExtractPdfTextResponse(recognizedText, 1));

        String extracted = textExtractor.extractText(scannedPdf);

        assertThat(extracted).isEqualTo(recognizedText);
        verify(mlClient).extractPdfText(any(byte[].class));
    }
}
