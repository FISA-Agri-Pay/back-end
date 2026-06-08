package com.kkpp.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_EXPIRY_MS = 60 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000L;
    private static final String TOKEN_TYPE = "Bearer";
    private static final String CLAIM_TOKEN_PURPOSE = "purpose";
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_PUBLIC_ID = "publicId";
    private static final String PURPOSE_REFRESH = "refresh";

    private final SecretKey secretKey;

    public JwtTokenProvider(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank.");
        }
        this.secretKey = Keys.hmacShaKeyFor(sha256(secret));
    }

    public String generateUserAccessToken(Long userId, UUID publicId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_PUBLIC_ID, String.valueOf(publicId))
                .claim("role", "USER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ACCESS_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public String generateAccessToken(UUID publicId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(publicId))
                .claim(CLAIM_PUBLIC_ID, String.valueOf(publicId))
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
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TOKEN_PURPOSE, PURPOSE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UUID publicId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(publicId))
                .claim(CLAIM_PUBLIC_ID, String.valueOf(publicId))
                .claim(CLAIM_TOKEN_PURPOSE, PURPOSE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public void validateRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!PURPOSE_REFRESH.equals(claims.get(CLAIM_TOKEN_PURPOSE, String.class))) {
                throw new JwtException("Not a refresh token.");
            }
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid refresh token: " + e.getMessage(), e);
        }
    }

    public long getAccessTokenExpirySeconds() {
        return ACCESS_TOKEN_EXPIRY_MS / 1000;
    }

    public long getRefreshTokenExpirySeconds() {
        return REFRESH_TOKEN_EXPIRY_MS / 1000;
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
