package com.kkpp.admin.adminauth.dto;

public record AdminAuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        AdminUserResponse admin
) {
}
