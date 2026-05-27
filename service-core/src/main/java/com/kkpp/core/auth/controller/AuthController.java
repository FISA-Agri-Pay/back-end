package com.kkpp.core.auth.controller;

import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
import com.kkpp.core.auth.dto.request.LoginRequest;
import com.kkpp.core.auth.dto.request.RefreshTokenRequest;
import com.kkpp.core.auth.dto.request.RegisterRequest;
import com.kkpp.core.auth.dto.request.SetPaymentPinRequest;
import com.kkpp.core.auth.dto.response.TokenResponse;
import com.kkpp.core.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입 · 로그인 · 토큰 관리 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "전화번호 · 이름 · 주소 · 계정 비밀번호(8~20자)로 가입합니다. 가입 완료 시 토큰이 즉시 발급됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 전화번호")
    })
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(
            @RequestBody @Valid RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.register(request), "회원가입이 완료되었습니다."));
    }

    @Operation(summary = "결제 PIN 등록", description = "숫자 6자리 결제 PIN을 등록합니다. Access Token이 필요합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "결제 PIN 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @PostMapping("/register/payment-pin")
    public ResponseEntity<ApiResponse<Void>> setPaymentPin(
            @AuthUser AuthUserInfo authUser,
            @RequestBody @Valid SetPaymentPinRequest request
    ) {
        authService.setPaymentPin(authUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "결제 비밀번호가 설정되었습니다."));
    }

    @Operation(summary = "로그인", description = "전화번호 + 계정 비밀번호로 로그인합니다. 응답의 isPinSet으로 결제 PIN 등록 여부를 확인할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "비밀번호 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 사용자")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @Operation(summary = "Access Token 재발급", description = "Refresh Token으로 새 토큰을 발급합니다. Refresh Token은 매 발급 시 교체됩니다(Rotation).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token")
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody @Valid RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }
}