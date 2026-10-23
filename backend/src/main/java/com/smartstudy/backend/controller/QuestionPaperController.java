package com.smartstudy.backend.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.dto.QuestionPaperResponse;
import com.smartstudy.backend.model.QuestionPaper;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.QuestionPaperService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api")
public class QuestionPaperController {

    private final QuestionPaperService paperService;
    private final UserService userService;

    public QuestionPaperController(QuestionPaperService paperService, UserService userService) {
        this.paperService = paperService;
        this.userService = userService;
    }

    @PostMapping(value = "/subjects/{subjectId}/papers",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QuestionPaperResponse> upload(
            @PathVariable Long subjectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "examYear", required = false) Integer examYear,
            Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        QuestionPaper paper = paperService.upload(subjectId, file, examYear, user);
        return ResponseEntity.ok(toResponse(paper));
    }

    @GetMapping("/subjects/{subjectId}/papers")
    public ResponseEntity<List<QuestionPaperResponse>> list(@PathVariable Long subjectId,
                                                              Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        List<QuestionPaperResponse> papers = paperService.listForSubject(subjectId, user)
                .stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(papers);
    }

    @GetMapping("/papers/{id}/text")
    public ResponseEntity<String> getText(@PathVariable Long id, Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        return ResponseEntity.ok(paperService.getForUser(id, user).getExtractedText());
    }

    @DeleteMapping("/papers/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User user = userService.getCurrentUser(authentication);
        paperService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    private QuestionPaperResponse toResponse(QuestionPaper p) {
        String text = p.getExtractedText();
        return new QuestionPaperResponse(p.getId(), p.getOriginalFilename(), p.getExamYear(),
                p.getUploadedAt(), p.getSubject().getId(), text == null ? 0 : text.length());
    }
}