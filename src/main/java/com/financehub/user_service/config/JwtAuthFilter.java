package com.financehub.user_service.config;

import com.financehub.user_service.service.JwtService;
import com.financehub.user_service.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.io.IOException;

/**
 * Spring Security filter that intercepts every HTTP request and validates
 * JWT tokens from the Authorization header.
 *
 * Extends OncePerRequestFilter — Spring guarantees this runs exactly once
 * per request regardless of filter chain configuration.
 *
 * Filter logic:
 * 1. Extract Bearer token from Authorization header
 * 2. Parse email and validate token signature and expiry
 * 3. Verify user exists and is currently active
 * 4. Set authentication in SecurityContext with user's role as authority
 *
 * Requests without a valid token are passed through unauthenticated —
 * SecurityConfig determines which endpoints require authentication.
 *
 * @see SecurityConfig
 * @see JwtService
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Extract Authorization header
        String authHeader = request.getHeader("Authorization");

        // If no token present, continue without authentication
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract token from header
        String token = authHeader.substring(7);

        try {
            // Extract email from token
            String email = jwtService.extractEmail(token);

            // If email found and no existing authentication
            if (email != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                // Verify user exists and is active
                userService.findCurrentByEmail(email).ifPresent(user -> {
                    // Validate token against user
                    if (jwtService.isTokenValid(token, email)) {
                        // Build authority from role
                        SimpleGrantedAuthority authority =
                                new SimpleGrantedAuthority(user.getRole().name());

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        email, null, List.of(authority));

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request));

                        // Set authentication in security context
                        SecurityContextHolder.getContext()
                                .setAuthentication(authToken);
                    }
                });
            }
        } catch (Exception e) {
            // Invalid token — continue without authentication
            // Security config will reject unauthorised requests
        }

        filterChain.doFilter(request, response);
    }
}