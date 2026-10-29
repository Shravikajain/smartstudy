package com.smartstudy.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.dto.InterviewAnswerRequest;
import com.smartstudy.backend.dto.InterviewReviewResponse;
import com.smartstudy.backend.dto.InterviewSummary;
import com.smartstudy.backend.dto.InterviewTurnResponse;
import com.smartstudy.backend.dto.StartInterviewRequest;
import com.smartstudy.backend.dto.ml.MlTranscriptionResponse;
import com.smartstudy.backend.service.InterviewService;
import com.smartstudy.backend.service.MlClient;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api")
public class InterviewController {

    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final InterviewService interviewService;
    private final MlClient mlClient;
    private final UserService userService;

    public InterviewController(InterviewService interviewService, MlClient mlClient, UserService userService) {
        this.interviewService = interviewService;
        this.mlClient = mlClient;
        this.userService = userService;
    }

    @PostMapping("/interviews")
    public ResponseEntity<InterviewTurnResponse> start(@RequestBody StartInterviewRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(interviewService.start(request, userService.getCurrentUser(authentication)));
    }

    @PostMapping("/interviews/{sessionId}/answer")
    public ResponseEntity<InterviewTurnResponse> answer(@PathVariable Long sessionId,
                                                        @RequestBody InterviewAnswerRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(interviewService.answer(sessionId, request.answer(),
                userService.getCurrentUser(authentication)));
    }

    @GetMapping("/interviews")
    public ResponseEntity<List<InterviewSummary>> history(Authentication authentication) {
        return ResponseEntity.ok(interviewService.history(userService.getCurrentUser(authentication)));
    }

    @GetMapping("/interviews/{sessionId}")
    public ResponseEntity<InterviewReviewResponse> review(@PathVariable Long sessionId,
                                                          Authentication authentication) {
        return ResponseEntity.ok(interviewService.review(sessionId, userService.getCurrentUser(authentication)));
    }

    @PostMapping(value = "/interview/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MlTranscriptionResponse> transcribeAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "language", required = false) String language,
            Authentication authentication) {
        userService.getCurrentUser(authentication);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Record an answer before requesting transcription");
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("The audio recording is too large (10 MB maximum)");
        }
        try {
            return ResponseEntity.ok(mlClient.transcribeAudio(
                    file.getBytes(), file.getOriginalFilename() == null
                            ? "interview-answer.webm" : file.getOriginalFilename(), language));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the audio recording", exception);
        }
    }
}
