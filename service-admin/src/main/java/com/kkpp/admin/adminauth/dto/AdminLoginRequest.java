package com.kkpp.admin.adminauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 로그인 요청")
public record AdminLoginRequest(
        @Schema(description = "관리자 이메일", example = "admin@example.com")
        @NotBlank(message = "아이디는 필수입니다.")
        @Email(message = "아이디는 이메일 형식이어야 합니다.")
        String email,

        @Schema(description = "관리자 비밀번호", example = "Admin1234!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
