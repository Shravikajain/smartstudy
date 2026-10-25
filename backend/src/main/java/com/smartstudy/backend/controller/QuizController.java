package com.smartstudy.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.dto.AnswerRequest;
import com.smartstudy.backend.dto.AnswerResult;
import com.smartstudy.backend.dto.QuestionBankResponse;
import com.smartstudy.backend.dto.QuizHistoryItem;
import com.smartstudy.backend.dto.QuizReviewResponse;
import com.smartstudy.backend.dto.StartQuizRequest;
import com.smartstudy.backend.dto.StartQuizResponse;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.QuizHistoryService;
import com.smartstudy.backend.service.QuizService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api")
public class QuizController {

    private final QuizService quizService;
    private final QuizHistoryService historyService;
    private final UserService userService;

    public QuizController(QuizService quizService, QuizHistoryService historyService,
                          UserService userService) {
        this.quizService = quizService;
        this.historyService = historyService;
        this.userService = userService;
    }

    @GetMapping("/subjects/{subjectId}/question-bank")
    public ResponseEntity<QuestionBankResponse> questionBank(@PathVariable Long subjectId,
                                                             Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        return ResponseEntity.ok(historyService.questionBank(subjectId, user));
    }

    @GetMapping("/quizzes")
    public ResponseEntity<List<QuizHistoryItem>> history(Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        return ResponseEntity.ok(historyService.history(user));
    }

    @GetMapping("/quizzes/{sessionId}")
    public ResponseEntity<QuizReviewResponse> review(@PathVariable Long sessionId,
                                                     Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        return ResponseEntity.ok(historyService.review(sessionId, user));
    }

    @PostMapping("/subjects/{subjectId}/quiz")
    public ResponseEntity<StartQuizResponse> start(@PathVariable Long subjectId,
                                                   @RequestBody(required = false) StartQuizRequest request,
                                                   Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        Integer maxQuestions = request == null ? null : request.getMaxQuestions();
        return ResponseEntity.ok(quizService.start(subjectId, maxQuestions,
                request == null ? null : request.getQuestionIds(),
                request == null ? null : request.getQuestionType(), user));
    }

    @PostMapping("/quiz/{sessionId}/answer")
    public ResponseEntity<AnswerResult> answer(@PathVariable Long sessionId,
                                               @RequestBody AnswerRequest request,
                                               Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        return ResponseEntity.ok(quizService.answer(sessionId, request.getSelectedIndex(), user));
    }

    @PostMapping(value = "/quiz/{sessionId}/subjective-answer",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnswerResult> answerSubjective(
            @PathVariable Long sessionId,
            @RequestParam(value = "answerText", required = false) String answerText,
            @RequestParam(value = "answerPdf", required = false) MultipartFile answerPdf,
            Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        return ResponseEntity.ok(quizService.answerSubjective(sessionId, answerText, answerPdf, user));
    }
}
