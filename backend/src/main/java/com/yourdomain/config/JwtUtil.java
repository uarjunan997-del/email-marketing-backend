package com.yourdomain.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

@Component
public class JwtUtil {
    private final long EXPIRATION = 86400000; // 1 day

    @Value("${security.jwt.secret:CHANGE_ME_SUPER_LONG_SECRET}")
    private String secretValue;
    private SecretKey cachedKey;

    private SecretKey key() {
        if (cachedKey == null) {
            if (secretValue == null || secretValue.isBlank()) {
                throw new IllegalStateException("JWT secret not configured");
            }
            if (secretValue.length() < 32) {
                throw new IllegalStateException("JWT secret too short (>=32 chars)");
            }
            cachedKey = Keys.hmacShaKeyFor(secretValue.getBytes(StandardCharsets.UTF_8));
        }
        return cachedKey;
    }

    public String generateToken(String username) {
        return Jwts.builder()
            .setSubject(username)
            .claim("username", username)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
            .signWith(key(), SignatureAlgorithm.HS256)
            .compact();
    }

    public String extractUsername(String token) { return getClaims(token).getSubject(); }
    public boolean validateToken(String token, String username) { return extractUsername(token).equals(username) && !isExpired(token); }

    private Claims getClaims(String token) { return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token).getBody(); }
    private boolean isExpired(String token) { return getClaims(token).getExpiration().before(new Date()); }
}
