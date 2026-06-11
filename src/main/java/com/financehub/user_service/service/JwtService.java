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

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // Generate a JWT token for a user
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

    // Validate token
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