package com.kkpp.core.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^\\d{10,11}$", message = "전화번호 형식이 올바르지 않습니다.") String phone,
        @NotBlank @Size(max = 50) String name,
        @NotBlank String address,
        String addressDetail,
        @NotBlank @Size(min = 5, max = 10) String zipCode,
        String residentId,
        @NotBlank @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다.") String password
) {
}