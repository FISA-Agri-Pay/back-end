package com.kkpp.auth.controller;

import com.kkpp.auth.dto.request.LoginRequest;
import com.kkpp.auth.dto.request.RefreshTokenRequest;
import com.kkpp.auth.dto.request.RegisterRequest;
import com.kkpp.auth.dto.request.SetPaymentPinRequest;
import com.kkpp.auth.dto.response.TokenResponse;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import com.kkpp.auth.service.AuthService;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.security.annotation.AuthUser;
import com.kkpp.common.security.auth.AuthUserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입, 로그인, 토큰, 결제 PIN 관리 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final AuthService authService;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    @Operation(
            summary = "회원가입",
            description = "휴대폰 번호, 이름, 주소, 주민등록번호, 계정 비밀번호를 등록합니다. 회원가입 시 토큰은 발급하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 사용자")
    })
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @RequestBody @Valid RegisterRequest request
    ) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Void>success(null, "회원가입이 완료되었습니다. 로그인 후 서비스를 이용해 주세요."));
    }

    @Operation(
            summary = "결제 PIN 등록",
            description = "인증된 사용자의 6자리 결제 PIN을 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "결제 PIN 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/register/payment-pin")
    public ResponseEntity<ApiResponse<Void>> setPaymentPin(
            @AuthUser AuthUserInfo authUser,
            @RequestBody @Valid SetPaymentPinRequest request
    ) {
        authService.setPaymentPin(authUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "결제 PIN이 등록되었습니다."));
    }

    @Operation(
            summary = "로그인",
            description = "휴대폰 번호와 계정 비밀번호로 로그인하고 access token과 refresh token을 발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "비밀번호 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response
    ) {
        TokenResponse tokenResponse = authService.login(request);
        // refreshToken은 응답 본문에 노출하지 않고 HttpOnly 쿠키로만 전달합니다.
        addRefreshTokenCookie(response, tokenResponse.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(tokenResponse, "로그인이 완료되었습니다."));
    }

    @Operation(
            summary = "Access Token 재발급",
            description = "refresh token을 검증한 뒤 새로운 access token과 refresh token을 발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 refresh token")
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 토큰 재발급은 클라이언트가 보낸 HttpOnly refreshToken 쿠키를 기준으로 처리합니다.
        String refreshToken = readRefreshTokenCookie(request);
        TokenResponse tokenResponse = authService.refresh(new RefreshTokenRequest(refreshToken));
        addRefreshTokenCookie(response, tokenResponse.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(tokenResponse, "토큰이 재발급되었습니다."));
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                // 로컬 HTTP와 운영 HTTPS 환경을 모두 지원하도록 설정값으로 Secure 여부를 제어합니다.
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
