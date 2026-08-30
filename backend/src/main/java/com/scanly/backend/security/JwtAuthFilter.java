package com.scanly.backend.security;

import com.scanly.backend.entity.User;
import com.scanly.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT Authentication Filter.
 *
 * This filter runs ONCE per HTTP request (before the controller).
 * It checks for a valid JWT in the Authorization header and
 * sets the authenticated user in Spring Security's context.
 *
 * Flow:
 *   1. Extract "Authorization: Bearer <token>" header
 *   2. Validate the token (signature + expiration)
 *   3. Load the user from the database
 *   4. Set the user as authenticated in SecurityContext
 *   5. Continue the filter chain → controller handles the request
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract the Authorization header
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract and validate the token
        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extract email from token and load user
        String email = jwtUtil.getEmailFromToken(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null) {
                // 4. Create authentication token with user's role
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        user,  // principal — the logged-in user object
                        null,  // credentials — not needed, we already validated the JWT
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                    );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 5. Set authentication in SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
