package com.kkpp.common.security.auth;

public record AuthUserInfo(
        Long userId,
        String role
) {
}
