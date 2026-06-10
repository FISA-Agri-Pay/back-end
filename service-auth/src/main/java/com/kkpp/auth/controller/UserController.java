package com.kkpp.auth.controller;

import com.kkpp.auth.dto.response.UserProfileResponse;
import com.kkpp.auth.service.UserService;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "마이페이지 사용자 정보 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 프로필 조회", description = "JWT 토큰으로 인증된 사용자의 이름, 휴대폰 번호, 주소 정보를 반환합니다.")
    public ApiResponse<UserProfileResponse> getMyProfile(@AuthUser AuthUserInfo authUser) {
        return ApiResponse.success(userService.getUserProfile(authUser.userId()));
    }
}
