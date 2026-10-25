package com.smartstudy.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartstudy.backend.model.QuestionPaper;
import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.QuestionPaperRepository;

@ExtendWith(MockitoExtension.class)
class QuestionPaperServiceTest {

    @Mock
    private QuestionPaperRepository paperRepository;

    @Mock
    private SubjectService subjectService;

    @Mock
    private FileStorageService storageService;

    @Mock
    private PdfTextExtractor textExtractor;

    @InjectMocks
    private QuestionPaperService questionPaperService;

    @Test
    void listRetriesOcrForPreviouslyUploadedScannedPaper() throws Exception {
        User owner = new User("owner@example.com", "hash", "Owner");
        Subject subject = new Subject("Science", owner);
        QuestionPaper paper = new QuestionPaper(
                "scan.pdf", "stored-scan.pdf", null, "", subject);
        Path storedPath = Path.of("stored-scan.pdf");
        String recognizedText = "Recognized question text. ".repeat(8);
        when(paperRepository.findBySubjectId(1L)).thenReturn(List.of(paper));
        when(storageService.resolve("stored-scan.pdf")).thenReturn(storedPath);
        when(textExtractor.extractText(storedPath)).thenReturn(recognizedText);
        when(paperRepository.save(any(QuestionPaper.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<QuestionPaper> result = questionPaperService.listForSubject(1L, owner);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExtractedText()).isEqualTo(recognizedText);
        verify(paperRepository).save(paper);
    }
}
