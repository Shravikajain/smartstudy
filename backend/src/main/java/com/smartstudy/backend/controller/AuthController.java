package com.smartstudy.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartstudy.backend.security.JwtUtil;

import com.smartstudy.backend.dto.LoginRequest;
import com.smartstudy.backend.dto.LoginResponse;
import com.smartstudy.backend.dto.RegisterRequest;
import com.smartstudy.backend.dto.UserResponse;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("SmartStudy backend is running");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.registerUser(
                    request.getEmail(), request.getPassword(), request.getFullName());
            UserResponse response = new UserResponse(user.getId(), user.getEmail(), user.getFullName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        boolean valid = userService.verifyLogin(request.getEmail(), request.getPassword());
        if (valid) {
        User user = userService.findByEmail(request.getEmail());
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new LoginResponse(token));
        }
        return ResponseEntity.status(401).body("Invalid credentials");
      }
}