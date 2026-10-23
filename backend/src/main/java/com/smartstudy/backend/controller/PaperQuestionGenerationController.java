package com.smartstudy.backend.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.dto.GeneratedQuestionResponse;
import com.smartstudy.backend.dto.GeneratedQuestionsResponse;
import com.smartstudy.backend.model.Question;
import com.smartstudy.backend.model.QuestionType;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.PaperQuestionGenerationService;
import com.smartstudy.backend.service.QuestionPaperService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api/subjects/{subjectId}/questions")
public class PaperQuestionGenerationController {

    private static final int MAX_PAPERS_PER_REQUEST = 5;

    private final QuestionPaperService paperService;
    private final PaperQuestionGenerationService generationService;
    private final UserService userService;

    public PaperQuestionGenerationController(QuestionPaperService paperService,
                                             PaperQuestionGenerationService generationService,
                                             UserService userService) {
        this.paperService = paperService;
        this.generationService = generationService;
        this.userService = userService;
    }

    @PostMapping(value = "/generate-from-papers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeneratedQuestionsResponse> generate(
            @PathVariable Long subjectId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("count") int count,
            @RequestParam(name = "format", defaultValue = "multiple_choice") String format,
            Authentication authentication) {
        if (files.isEmpty() || files.size() > MAX_PAPERS_PER_REQUEST) {
            throw new IllegalArgumentException("Upload between 1 and "
                    + MAX_PAPERS_PER_REQUEST + " past-paper PDFs");
        }
        if (count < 1 || count > 50) {
            throw new IllegalArgumentException("Question count must be between 1 and 50");
        }

        User user = userService.getCurrentUser(authentication);
        if (!List.of("multiple_choice", "subjective", "mixed").contains(format)) {
            throw new IllegalArgumentException(
                    "Question format must be multiple_choice, subjective, or mixed");
        }
        if (format.equals("mixed") && count < 2) {
            throw new IllegalArgumentException("Mixed format requires at least 2 questions");
        }
        for (MultipartFile file : files) {
            paperService.upload(subjectId, file, null, user);
        }

        PaperQuestionGenerationService.GeneratedSet generated =
                generationService.generate(subjectId, count, format, user);
        List<GeneratedQuestionResponse> questions = generated.questions().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(new GeneratedQuestionsResponse(generated.topics(), questions));
    }

    private GeneratedQuestionResponse toResponse(Question question) {
        return new GeneratedQuestionResponse(question.getId(), question.getPrompt(),
                question.getOptions(), question.getDifficulty(), question.getTopic().getName(),
                question.getQuestionType());
    }
}
