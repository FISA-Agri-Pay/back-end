package com.kkpp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(
                regexp = "^(01[016789]\\d{7,8}|01[016789]-\\d{3,4}-\\d{4})$",
                message = "휴대폰 번호는 01012345678 또는 010-1234-5678 형식이어야 합니다."
        )
        String phone,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "주소는 필수입니다.")
        String address,

        String addressDetail,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(min = 5, max = 10, message = "우편번호는 5자 이상 10자 이하여야 합니다.")
        String zipCode,

        @NotBlank(message = "주민등록번호는 필수입니다.")
        @Pattern(
                regexp = "^\\d{6}-?\\d{7}$",
                message = "주민등록번호는 123456-1234567 또는 1234561234567 형식이어야 합니다."
        )
        String residentId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
        String password
) {
}
