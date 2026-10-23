package com.smartstudy.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstudy.backend.dto.GenerateQuestionsRequest;
import com.smartstudy.backend.dto.QuestionResponse;
import com.smartstudy.backend.model.Question;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.QuestionGenerationService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api")
public class QuestionGenerationController {

    private final QuestionGenerationService generationService;
    private final UserService userService;

    public QuestionGenerationController(QuestionGenerationService generationService,
                                        UserService userService) {
        this.generationService = generationService;
        this.userService = userService;
    }

    @PostMapping("/topics/{topicId}/questions/generate")
    public ResponseEntity<List<QuestionResponse>> generate(@PathVariable Long topicId,
                                                            @RequestBody(required = false)
                                                            GenerateQuestionsRequest request,
                                                            Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        int count = request == null || request.getCount() == null ? 10 : request.getCount();
        List<QuestionResponse> questions = generationService.generate(topicId, count, user)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/topics/{topicId}/questions")
    public ResponseEntity<List<QuestionResponse>> list(@PathVariable Long topicId,
                                                        Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        List<QuestionResponse> questions = generationService.listForTopic(topicId, user)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(questions);
    }

    private QuestionResponse toResponse(Question question) {
        return new QuestionResponse(question.getId(), question.getPrompt(), question.getOptions(),
                question.getCorrectIndex(), question.getExplanation(), question.getDifficulty(),
                question.getTopic().getId());
    }
}
