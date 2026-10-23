package com.smartstudy.backend.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartstudy.backend.dto.SubjectRequest;
import com.smartstudy.backend.dto.SubjectResponse;
import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.SubjectService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;
    private final UserService userService;

    public SubjectController(SubjectService subjectService, UserService userService) {
        this.subjectService = subjectService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(@RequestBody SubjectRequest request,
                                                    Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        Subject subject = subjectService.createSubject(request.getName(), user);
        return ResponseEntity.ok(toResponse(subject));
    }

    @GetMapping
    public ResponseEntity<List<SubjectResponse>> list(Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        List<SubjectResponse> subjects = subjectService.getSubjectsForUser(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getOne(@PathVariable Long id, Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        Subject subject = subjectService.getSubjectByIdForUser(id, user);
        return ResponseEntity.ok(toResponse(subject));
    }

    private SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(subject.getId(), subject.getName());
    }
}