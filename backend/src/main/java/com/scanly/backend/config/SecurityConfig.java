package com.scanly.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration.
 *
 * FOR NOW (Feature 1): All endpoints are open — no authentication required.
 * This lets us test the health endpoint without dealing with JWT yet.
 *
 * In Feature 3 (Auth), we'll lock this down to require JWT tokens
 * on all endpoints except /api/v1/auth/** and /api/v1/health.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — we're a stateless REST API using JWT, not cookies
            .csrf(csrf -> csrf.disable())

            // Stateless sessions — no server-side session storage
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // TEMPORARY: Allow all requests (will be locked down in Feature 3)
            .authorizeHttpRequests(auth ->
                auth.anyRequest().permitAll()
            );

        return http.build();
    }
}
