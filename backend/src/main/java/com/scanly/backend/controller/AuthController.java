package com.scanly.backend.controller;

import com.scanly.backend.dto.AuthResponse;
import com.scanly.backend.dto.LoginRequest;
import com.scanly.backend.dto.RegisterRequest;
import com.scanly.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication Controller.
 *
 * Provides public endpoints for user registration and login.
 * These endpoints do NOT require a JWT token.
 *
 *   POST /api/v1/auth/register  → Create account + get JWT
 *   POST /api/v1/auth/login     → Authenticate + get JWT
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "REGISTRATION_FAILED", "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "INVALID_CREDENTIALS", "message", e.getMessage()));
        }
    }
}
