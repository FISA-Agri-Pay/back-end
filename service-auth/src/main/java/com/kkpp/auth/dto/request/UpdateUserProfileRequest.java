package com.kkpp.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원정보(주소) 수정 요청")
public record UpdateUserProfileRequest(
        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        @NotBlank(message = "주소는 필수입니다.")
        String address,

        @Schema(description = "상세 주소", example = "101호")
        String addressDetail,

        @Schema(description = "우편번호", example = "12345")
        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(min = 5, max = 10, message = "우편번호는 5자 이상 10자 이하여야 합니다.")
        String zipCode
) {
}
