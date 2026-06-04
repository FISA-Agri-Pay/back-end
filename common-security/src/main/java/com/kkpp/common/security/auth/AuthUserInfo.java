package com.kkpp.common.security.auth;

import java.util.UUID;

public record AuthUserInfo(
        Long userId,
        UUID publicId,
        String role
) {

    public AuthUserInfo(Long userId, String role) {
        this(userId, null, role);
    }

    public AuthUserInfo(UUID publicId, String role) {
        this(null, publicId, role);
    }
}
