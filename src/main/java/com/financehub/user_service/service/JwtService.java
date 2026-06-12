package com.financehub.user_service.service;

import com.financehub.user_service.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Service responsible for JWT token generation and validation.
 *
 * Tokens are signed using HMAC-SHA256 with a secret key loaded from
 * environment configuration — never hardcoded in source code.
 *
 * Token payload contains:
 * - subject: user email address
 * - userId: generated sequence ID
 * - role: user role for authorisation decisions
 * - issuedAt: token creation timestamp
 * - expiration: configurable expiry (default 24 hours)
 *
 * @see JwtAuthFilter
 * @see SecurityConfig
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * Generates a signed JWT token for an authenticated user.
     * Token is signed with HMAC-SHA256 using the configured secret key.
     *
     * @param userId the user's generated sequence ID included as a claim
     * @param email the user's email address used as the token subject
     * @param role the user's role included as a claim for authorisation
     * @return signed JWT token string
     */
    public String generateToken(Long userId, String email, Role role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // Extract email from token
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // Extract userId from token
    public Long extractUserId(String token) {
        return extractClaims(token).get("userId", Long.class);
    }

    // Get token expiry as LocalDateTime
    public LocalDateTime extractExpiration(String token) {
        Date expiryDate = extractClaims(token).getExpiration();
        return expiryDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Validates a JWT token against an expected email address.
     * Checks both that the email matches the token subject and that
     * the token has not expired.
     *
     * Returns false rather than throwing for invalid tokens — callers
     * should treat false as unauthenticated rather than an error condition.
     *
     * @param token the JWT token string to validate
     * @param email the expected email address to match against
     * @return true if token is valid and not expired, false otherwise
     */
    public boolean isTokenValid(String token, String email) {
        try {
            String extractedEmail = extractEmail(token);
            return extractedEmail.equals(email) && isTokenStillValid(token);
        } catch (Exception e) {
            return false;
        }
    }

    // Check if token is still within its validity period
    private boolean isTokenStillValid(String token) {
        return new Date().before(
                extractClaims(token).getExpiration()
        );
    }

    // Extract all claims from token
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Build signing key from secret
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
}