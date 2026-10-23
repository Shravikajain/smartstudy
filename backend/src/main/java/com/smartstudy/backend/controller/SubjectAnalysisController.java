package com.smartstudy.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstudy.backend.dto.SubjectAnalysisResponse;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.SubjectAnalysisService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api/subjects/{subjectId}/analysis")
public class SubjectAnalysisController {

    private final SubjectAnalysisService analysisService;
    private final UserService userService;

    public SubjectAnalysisController(SubjectAnalysisService analysisService, UserService userService) {
        this.analysisService = analysisService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<SubjectAnalysisResponse> getAnalysis(
            @PathVariable Long subjectId, Authentication authentication) {
        return analyze(subjectId, authentication);
    }

    @PostMapping
    public ResponseEntity<SubjectAnalysisResponse> runAnalysis(
            @PathVariable Long subjectId, Authentication authentication) {
        return analyze(subjectId, authentication);
    }

    private ResponseEntity<SubjectAnalysisResponse> analyze(Long subjectId, Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        return ResponseEntity.ok(analysisService.analyze(subjectId, user));
    }
}
