package com.kkpp.admin.adminauth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLogoutRequest(
        @NotBlank(message = "refresh token은 필수입니다.")
        String refreshToken
) {
}
