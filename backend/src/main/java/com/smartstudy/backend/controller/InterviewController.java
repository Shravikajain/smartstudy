package com.smartstudy.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstudy.backend.dto.ml.MlInterviewTurnRequest;
import com.smartstudy.backend.dto.ml.MlInterviewTurnResponse;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.MlClient;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private static final List<String> TARGET_TYPES = List.of("subject", "engineering_role");
    private static final List<String> LEVELS = List.of("beginner", "intermediate", "advanced");

    private final MlClient mlClient;
    private final UserService userService;

    public InterviewController(MlClient mlClient, UserService userService) {
        this.mlClient = mlClient;
        this.userService = userService;
    }

    @PostMapping("/turn")
    public ResponseEntity<MlInterviewTurnResponse> interviewTurn(
            @RequestBody MlInterviewTurnRequest request,
            Authentication authentication) {
        userService.getCurrentUser(authentication);
        if (request.targetType() == null || !TARGET_TYPES.contains(request.targetType())) {
            throw new IllegalArgumentException("Choose a subject or engineering role");
        }
        if (request.target() == null || request.target().isBlank() || request.target().length() > 120) {
            throw new IllegalArgumentException("Interview target must be 1 to 120 characters");
        }
        if (request.level() == null || !LEVELS.contains(request.level())) {
            throw new IllegalArgumentException("Choose beginner, intermediate, or advanced level");
        }
        if (request.questionCount() < 1 || request.questionCount() > 20
                || request.questionNumber() < 0
                || request.questionNumber() > request.questionCount()) {
            throw new IllegalArgumentException("Interview question count must be between 1 and 20");
        }
        if (request.history() == null || request.history().size() > 40
                || request.history().stream().anyMatch(message -> message == null
                || message.content() == null || message.content().isBlank()
                || message.content().length() > 12000
                || message.speaker() == null
                || !List.of("interviewer", "candidate").contains(message.speaker()))) {
            throw new IllegalArgumentException("Interview history is invalid or too long");
        }
        if (request.history().stream().mapToInt(message -> message.content().length()).sum() > 24000) {
            throw new IllegalArgumentException("Interview history exceeds the 24,000-character limit");
        }
        if (request.questionNumber() == 0 && request.answer() != null
                || request.questionNumber() > 0 && (request.answer() == null
                || request.answer().isBlank() || request.answer().length() < 10
                || request.answer().length() > 12000)) {
            throw new IllegalArgumentException("Provide an answer for the current interview question");
        }
        return ResponseEntity.ok(mlClient.interviewTurn(request));
    }
}
