package com.kkpp.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record TokenResponse(
        String accessToken,
        @JsonIgnore
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean isPinSet
) {
}
