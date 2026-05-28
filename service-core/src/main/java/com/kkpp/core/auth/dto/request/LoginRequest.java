package com.kkpp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank @Pattern(regexp = "^\\d{10,11}$", message = "전화번호 형식이 올바르지 않습니다.") String phone,
        @NotBlank String password
) {
}