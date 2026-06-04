package com.kkpp.admin.adminauth.service;

import com.kkpp.admin.adminauth.domain.AdminAuthUser;
import com.kkpp.admin.adminauth.dto.AdminAuthResponse;
import com.kkpp.admin.adminauth.dto.AdminLoginRequest;
import com.kkpp.admin.adminauth.dto.AdminTokenRefreshRequest;
import com.kkpp.admin.adminauth.dto.AdminUserResponse;
import com.kkpp.admin.adminauth.repository.AdminAuthUserRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.security.auth.AuthUserInfo;
import com.kkpp.common.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {

    private final AdminAuthUserRepository adminAuthUserRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AdminAuthResponse login(AdminLoginRequest request) {
        String email = normalizeEmail(request.email());
        AdminAuthUser adminUser = adminAuthUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    log.warn("관리자 로그인 실패: 등록되지 않은 아이디입니다. 이메일={}", maskEmail(email));
                    return loginFailed();
                });

        if (!passwordEncoder.matches(request.password(), adminUser.getPasswordHash())) {
            log.warn("관리자 로그인 실패: 비밀번호가 일치하지 않습니다. 관리자공개ID={}", adminUser.getPublicId());
            throw loginFailed();
        }

        validateActiveAdmin(adminUser, "관리자 로그인");

        adminUser.recordLogin();
        AdminAuthResponse response = issueTokens(adminUser);
        log.info("관리자 로그인이 완료되었습니다. 관리자공개ID={}", adminUser.getPublicId());
        return response;
    }

    @Transactional
    public AdminAuthResponse refresh(AdminTokenRefreshRequest request) {
        validateRefreshToken(request.refreshToken(), "관리자 토큰 갱신");

        AdminAuthUser adminUser = adminAuthUserRepository.findByRefreshToken(hashToken(request.refreshToken()))
                .orElseThrow(() -> {
                    log.warn("관리자 토큰 갱신 실패: 저장된 리프레시 토큰과 일치하지 않습니다.");
                    return invalidRefreshToken();
                });
        validateActiveAdmin(adminUser, "관리자 토큰 갱신");

        AdminAuthResponse response = issueTokens(adminUser);
        log.info("관리자 토큰 갱신이 완료되었습니다. 관리자공개ID={}", adminUser.getPublicId());
        return response;
    }

    @Transactional
    public void logout(AuthUserInfo authUser) {
        if (isAdminRole(authUser)) {
            logoutByAuthenticatedAdmin(authUser);
            return;
        }

        if (authUser != null) {
            log.warn("관리자 로그아웃 실패: 관리자 권한이 아닌 토큰입니다. 역할={}", authUser.role());
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }

        log.warn("관리자 로그아웃 실패: Authorization 헤더에 관리자 access token이 없습니다.");
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "관리자 인증 정보가 필요합니다.");
    }

    private void logoutByAuthenticatedAdmin(AuthUserInfo authUser) {
        if (authUser.publicId() == null) {
            log.warn("관리자 로그아웃 실패: access token에 관리자 공개 ID가 없습니다.");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "관리자 인증 정보가 올바르지 않습니다.");
        }

        AdminAuthUser adminUser = adminAuthUserRepository.findByPublicId(authUser.publicId())
                .orElseThrow(() -> {
                    log.warn("관리자 로그아웃 실패: 토큰의 관리자 공개 ID에 해당하는 계정을 찾을 수 없습니다. 관리자공개ID={}", authUser.publicId());
                    return new BusinessException(ErrorCode.UNAUTHORIZED, "관리자 정보를 찾을 수 없습니다.");
                });

        validateActiveAdmin(adminUser, "관리자 로그아웃");
        adminUser.clearRefreshToken();
        log.info("관리자 로그아웃이 완료되었습니다. 관리자공개ID={}", adminUser.getPublicId());
    }

    private AdminAuthResponse issueTokens(AdminAuthUser adminUser) {
        String accessToken = jwtTokenProvider.generateAccessToken(adminUser.getPublicId(), adminUser.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(adminUser.getPublicId());
        adminUser.updateRefreshToken(hashToken(refreshToken));

        return new AdminAuthResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getTokenType(),
                jwtTokenProvider.getAccessTokenExpirySeconds(),
                new AdminUserResponse(
                        adminUser.getPublicId(),
                        adminUser.getEmail(),
                        adminUser.getName(),
                        adminUser.getRole()
                )
        );
    }

    private void validateActiveAdmin(AdminAuthUser adminUser, String actionName) {
        if (!adminUser.isActive()) {
            log.warn("{} 실패: 비활성 관리자 계정입니다. 관리자공개ID={}, 상태={}",
                    actionName,
                    adminUser.getPublicId(),
                    adminUser.getStatus());
            throw new BusinessException(ErrorCode.FORBIDDEN, "활성 상태의 관리자 계정만 인증할 수 있습니다.");
        }
    }

    private void validateRefreshToken(String refreshToken, String actionName) {
        try {
            jwtTokenProvider.validateRefreshToken(refreshToken);
        } catch (IllegalArgumentException exception) {
            log.warn("{} 실패: 리프레시 토큰 검증에 실패했습니다. 사유={}", actionName, exception.getMessage());
            throw invalidRefreshToken();
        }
    }

    private boolean isAdminRole(AuthUserInfo authUser) {
        if (authUser == null || !StringUtils.hasText(authUser.role())) {
            return false;
        }
        return "ADMIN".equals(authUser.role()) || "SUPER_ADMIN".equals(authUser.role());
    }

    private BusinessException loginFailed() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "****";
        }
        return email.charAt(0) + "****" + email.substring(atIndex);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            log.error("관리자 인증 처리 실패: SHA-256 알고리즘을 사용할 수 없습니다.", exception);
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
