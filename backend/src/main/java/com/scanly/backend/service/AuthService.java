package com.scanly.backend.service;

import com.scanly.backend.dto.AuthResponse;
import com.scanly.backend.dto.LoginRequest;
import com.scanly.backend.dto.RegisterRequest;
import com.scanly.backend.entity.Organization;
import com.scanly.backend.entity.User;
import com.scanly.backend.entity.enums.UserRole;
import com.scanly.backend.repository.OrganizationRepository;
import com.scanly.backend.repository.UserRepository;
import com.scanly.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Authentication Service.
 *
 * Handles the business logic for user registration and login.
 *
 * Register flow:
 *   1. Check if email already exists → throw error if yes
 *   2. Create a new Organization
 *   3. Create a new User (with hashed password) as ADMIN of that org
 *   4. Generate a JWT token
 *   5. Return the token + user info
 *
 * Login flow:
 *   1. Find user by email → throw error if not found
 *   2. Verify password → throw error if wrong
 *   3. Generate a JWT token
 *   4. Return the token + user info
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Check if email is already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // 2. Create a new Organization
        Organization org = Organization.builder()
            .name(request.getOrganizationName())
            .build();
        org = organizationRepository.save(org);

        // 3. Create the User as ADMIN of the new org
        User user = User.builder()
            .organization(org)
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .role(UserRole.ADMIN)
            .build();
        user = userRepository.save(user);

        // 4. Generate JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        Date expiry = jwtUtil.getExpirationFromToken(token);

        // 5. Return response
        return AuthResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .role(user.getRole().name())
            .token(token)
            .expiresAt(expiry.toInstant())
            .build();
    }

    public AuthResponse login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // 2. Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // 3. Generate JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        Date expiry = jwtUtil.getExpirationFromToken(token);

        // 4. Return response
        return AuthResponse.builder()
            .userId(user.getId())
            .email(user.getEmail())
            .role(user.getRole().name())
            .token(token)
            .expiresAt(expiry.toInstant())
            .build();
    }
}
