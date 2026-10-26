package com.smartstudy.backend.service;

import java.util.regex.Pattern;

import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;

@Service
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    // Same shape the browser's email field accepts: text@domain, no spaces.
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(String email, String rawPassword, String fullName) {
        // The browser checks these too, but requests can reach the API without it.
        if (fullName == null || fullName.isBlank() || fullName.length() > 255) {
            throw new IllegalArgumentException("Enter your name (up to 255 characters)");
        }
        if (email == null || email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        String hashedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(email, hashedPassword, fullName);
        return userRepository.save(user);
    }

    public boolean verifyLogin(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .map(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
                .orElse(false);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User getCurrentUser(Authentication authentication) {
    String email = authentication.getName();
    return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
}
}