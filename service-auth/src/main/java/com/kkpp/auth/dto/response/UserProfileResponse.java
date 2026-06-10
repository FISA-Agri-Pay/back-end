package com.kkpp.auth.dto.response;

import com.kkpp.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마이페이지 사용자 프로필 응답")
public record UserProfileResponse(
        @Schema(description = "이름", example = "홍길동")
        String name,
        @Schema(description = "휴대폰 번호", example = "01012345678")
        String phone,
        @Schema(description = "주소", example = "서울시 강남구 테헤란로 123")
        String address,
        @Schema(description = "상세 주소", example = "101호")
        String addressDetail,
        @Schema(description = "우편번호", example = "12345")
        String zipCode
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getName(),
                user.getPhone(),
                user.getAddress(),
                user.getAddressDetail(),
                user.getZipCode()
        );
    }
}
