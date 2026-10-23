package com.smartstudy.backend.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/api/me")
    public String me(Authentication authentication) {
        return "You are logged in as: " + authentication.getName();
    }
}