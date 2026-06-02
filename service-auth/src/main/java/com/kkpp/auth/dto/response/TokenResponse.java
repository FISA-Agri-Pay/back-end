package com.kkpp.auth.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean isPinSet
) {
}
