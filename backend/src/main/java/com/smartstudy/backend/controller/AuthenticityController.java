package com.smartstudy.backend.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.dto.AuthenticityCheckResponse;
import com.smartstudy.backend.service.AuthenticityService;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api/authenticity")
public class AuthenticityController {

    private final AuthenticityService authenticityService;
    private final UserService userService;

    public AuthenticityController(AuthenticityService authenticityService, UserService userService) {
        this.authenticityService = authenticityService;
        this.userService = userService;
    }

    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthenticityCheckResponse> check(
            @RequestParam(name = "text", required = false) String text,
            @RequestParam(name = "file", required = false) MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(authenticityService.check(text, file,
                userService.getCurrentUser(authentication)));
    }

    @GetMapping("/checks")
    public ResponseEntity<List<AuthenticityCheckResponse>> recent(Authentication authentication) {
        return ResponseEntity.ok(authenticityService.recent(userService.getCurrentUser(authentication)));
    }
}
