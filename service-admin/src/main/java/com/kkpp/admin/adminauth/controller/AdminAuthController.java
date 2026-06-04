package com.kkpp.admin.adminauth.controller;

import com.kkpp.admin.adminauth.dto.AdminAuthResponse;
import com.kkpp.admin.adminauth.dto.AdminLoginRequest;
import com.kkpp.admin.adminauth.dto.AdminLogoutRequest;
import com.kkpp.admin.adminauth.dto.AdminTokenRefreshRequest;
import com.kkpp.admin.adminauth.service.AdminAuthService;
import com.kkpp.common.core.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 인증", description = "BNPL 백오피스 관리자 인증 API")
@RestController
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "아이디/비밀번호 로그인", description = "관리자 이메일과 비밀번호로 로그인하고 토큰을 발급합니다.")
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<AdminAuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request), "로그인이 완료되었습니다.");
    }

    @Operation(summary = "토큰 갱신", description = "refresh token으로 관리자 access token과 refresh token을 재발급합니다.")
    @SecurityRequirements
    @PostMapping("/token/refresh")
    public ApiResponse<AdminAuthResponse> refresh(@Valid @RequestBody AdminTokenRefreshRequest request) {
        return ApiResponse.success(adminAuthService.refresh(request), "토큰이 갱신되었습니다.");
    }

    @Operation(summary = "로그아웃", description = "관리자 refresh token을 무효화합니다.")
    @SecurityRequirements
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody AdminLogoutRequest request) {
        adminAuthService.logout(request);
        return ApiResponse.success(null, "로그아웃이 완료되었습니다.");
    }
}
