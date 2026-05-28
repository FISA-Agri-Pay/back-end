package com.kkpp.common.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import javax.crypto.SecretKey;

public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_EXPIRY_MS = 60 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000L;
    private static final String TOKEN_TYPE = "Bearer";

    private final SecretKey secretKey;

    public JwtTokenProvider(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank.");
        }
        this.secretKey = Keys.hmacShaKeyFor(sha256(secret));
    }

    public String generateAccessToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public long getAccessTokenExpirySeconds() {
        return ACCESS_TOKEN_EXPIRY_MS / 1000;
    }

    public String getTokenType() {
        return TOKEN_TYPE;
    }

    private byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", exception);
        }
    }
}