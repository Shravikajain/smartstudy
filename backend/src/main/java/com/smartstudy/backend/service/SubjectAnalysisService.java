package com.smartstudy.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.smartstudy.backend.dto.SubjectAnalysisResponse;
import com.smartstudy.backend.dto.ml.MlAnalyzeRequest;
import com.smartstudy.backend.dto.ml.MlAnalyzeResponse;
import com.smartstudy.backend.dto.ml.MlPaperIn;
import com.smartstudy.backend.model.QuestionPaper;
import com.smartstudy.backend.model.User;

@Service
public class SubjectAnalysisService {

    private final QuestionPaperService paperService;
    private final MlClient mlClient;

    public SubjectAnalysisService(QuestionPaperService paperService, MlClient mlClient) {
        this.paperService = paperService;
        this.mlClient = mlClient;
    }

    public SubjectAnalysisResponse analyze(Long subjectId, User owner) {
        List<MlPaperIn> papers = paperService.listForSubject(subjectId, owner).stream()
                .map(this::toMlPaper)
                .toList();
        if (papers.isEmpty()) {
            throw new IllegalArgumentException("Upload at least one past paper before running analysis");
        }

        MlAnalyzeResponse result = mlClient.analyze(new MlAnalyzeRequest(papers, 8, 0.6));
        return new SubjectAnalysisResponse(result, LocalDateTime.now());
    }

    private MlPaperIn toMlPaper(QuestionPaper paper) {
        String text = paper.getExtractedText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Paper " + paper.getOriginalFilename() + " has no readable text");
        }
        return new MlPaperIn(paper.getId(), paper.getExamYear(), text);
    }
}
