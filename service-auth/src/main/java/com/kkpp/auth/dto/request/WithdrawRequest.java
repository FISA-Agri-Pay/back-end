package com.kkpp.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청")
public record WithdrawRequest(
        @Schema(description = "계정 비밀번호", example = "password12")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
