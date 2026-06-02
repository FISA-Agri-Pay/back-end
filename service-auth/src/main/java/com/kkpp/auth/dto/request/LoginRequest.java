package com.kkpp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(
                regexp = "^(01[016789]\\d{7,8}|01[016789]-\\d{3,4}-\\d{4})$",
                message = "휴대폰 번호는 01012345678 또는 010-1234-5678 형식이어야 합니다."
        )
        String phone,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
