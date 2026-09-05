package com.scanly.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Utility Class.
 *
 * Handles all JWT operations:
 *   - Generating tokens after login/register
 *   - Extracting user info (email, userId) from tokens
 *   - Validating tokens on every request
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
        @Value("${scanly.jwt.secret}") String secret,
        @Value("${scanly.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("Missing JWT secret! Set SCANLY_JWT_SECRET in your .env file or environment.");
        }
        byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("SCANLY_JWT_SECRET must decode to at least 256 bits (32 bytes). Check .env file.");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a JWT token for a user.
     */
    public String generateToken(UUID userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .subject(email)
            .claim("userId", userId.toString())
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact();
    }

    /**
     * Extract the email (subject) from a token.
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extract the userId from a token.
     */
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).get("userId", String.class));
    }

    /**
     * Extract the expiration date from a token.
     */
    public Date getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * Validate a token — checks signature and expiration.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
