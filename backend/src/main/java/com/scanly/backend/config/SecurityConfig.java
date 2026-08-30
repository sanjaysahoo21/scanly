package com.scanly.backend.config;

import com.scanly.backend.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security Configuration.
 *
 * Now locked down with JWT authentication (Feature 3).
 *
 * PUBLIC endpoints (no token needed):
 *   - /api/v1/auth/**  (register, login)
 *   - /api/v1/health   (health check)
 *
 * PROTECTED endpoints (require valid JWT):
 *   - Everything else under /api/v1/**
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS — allow React dev server to call the backend
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Disable CSRF — we're a stateless REST API using JWT
            .csrf(csrf -> csrf.disable())

            // Stateless sessions — no server-side session storage
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Endpoint authorization rules
            .authorizeHttpRequests(auth -> auth
                // Allow CORS preflight requests through (browser sends OPTIONS before every cross-origin request)
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // Public endpoints — no JWT required
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/health").permitAll()
                // Allow Spring's internal /error forwarding (used when validation fails, etc.)
                .requestMatchers("/error").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Add our JWT filter BEFORE Spring's default auth filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration.
     * Allows requests from the React frontend dev server.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow React dev server on both Vite default ports
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * BCrypt password encoder — used to hash passwords before storing in DB.
     * BCrypt automatically handles salting and is resistant to brute-force attacks.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
