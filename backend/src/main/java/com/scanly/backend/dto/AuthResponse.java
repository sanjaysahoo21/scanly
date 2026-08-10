package com.scanly.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body returned after successful register or login.
 */
@Data
@Builder
@AllArgsConstructor
public class AuthResponse {

    private UUID userId;
    private String email;
    private String role;
    private String token;
    private Instant expiresAt;
}
