package com.kkpp.admin.adminauth.service;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.adminAuthUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.admin.adminauth.domain.AdminAuthUser;
import com.kkpp.admin.adminauth.dto.AdminLoginRequest;
import com.kkpp.admin.adminauth.dto.AdminTokenRefreshRequest;
import com.kkpp.admin.adminauth.repository.AdminAuthUserRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.security.auth.AuthUserInfo;
import com.kkpp.common.security.jwt.JwtTokenProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    private static final UUID ADMIN_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private AdminAuthUserRepository adminAuthUserRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        adminAuthService = new AdminAuthService(adminAuthUserRepository, jwtTokenProvider, passwordEncoder);
    }

    @Test
    void loginIssuesTokensAndStoresRefreshHash() {
        AdminAuthUser adminUser = adminAuthUser(ADMIN_PUBLIC_ID, "ADMIN", "ACTIVE");
        when(adminAuthUserRepository.findByEmailIgnoreCase("admin@kkpp.com")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        stubTokenIssue();

        var response = adminAuthService.login(new AdminLoginRequest(" ADMIN@KKPP.COM ", "password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(adminUser.getLastLoginAt()).isNotNull();
        assertThat(adminUser.getRefreshToken()).isNotBlank();
    }

    @Test
    void loginRejectsUnknownPasswordInactiveAndNonAdminUsers() {
        when(adminAuthUserRepository.findByEmailIgnoreCase("none@kkpp.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("none@kkpp.com", "password")))
                .isInstanceOf(BusinessException.class);

        AdminAuthUser activeAdmin = adminAuthUser(ADMIN_PUBLIC_ID, "ADMIN", "ACTIVE");
        when(adminAuthUserRepository.findByEmailIgnoreCase("admin@kkpp.com")).thenReturn(Optional.of(activeAdmin));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("admin@kkpp.com", "wrong")))
                .isInstanceOf(BusinessException.class);

        AdminAuthUser inactive = adminAuthUser(ADMIN_PUBLIC_ID, "ADMIN", "INACTIVE");
        when(adminAuthUserRepository.findByEmailIgnoreCase("inactive@kkpp.com")).thenReturn(Optional.of(inactive));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("inactive@kkpp.com", "password")))
                .isInstanceOf(BusinessException.class);

        AdminAuthUser userRole = adminAuthUser(ADMIN_PUBLIC_ID, "USER", "ACTIVE");
        when(adminAuthUserRepository.findByEmailIgnoreCase("user@kkpp.com")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.login(new AdminLoginRequest("user@kkpp.com", "password")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refreshValidatesTokenAndIssuesNewTokens() {
        AdminAuthUser adminUser = adminAuthUser(ADMIN_PUBLIC_ID, "ADMIN", "ACTIVE");
        when(adminAuthUserRepository.findByRefreshToken(anyString())).thenReturn(Optional.of(adminUser));
        stubTokenIssue();

        var response = adminAuthService.refresh(new AdminTokenRefreshRequest("old-refresh-token"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(jwtTokenProvider).validateRefreshToken("old-refresh-token");
        assertThat(adminUser.getRefreshToken()).isNotBlank();
    }

    @Test
    void refreshRejectsInvalidRefreshToken() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("invalid"))
                .when(jwtTokenProvider).validateRefreshToken("bad-token");

        assertThatThrownBy(() -> adminAuthService.refresh(new AdminTokenRefreshRequest("bad-token")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void logoutClearsRefreshTokenForAuthenticatedAdmin() {
        AdminAuthUser adminUser = adminAuthUser(ADMIN_PUBLIC_ID, "ADMIN", "ACTIVE");
        adminUser.updateRefreshToken("refresh-hash");
        when(adminAuthUserRepository.findByPublicId(ADMIN_PUBLIC_ID)).thenReturn(Optional.of(adminUser));

        adminAuthService.logout(new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"));

        assertThat(adminUser.getRefreshToken()).isNull();
    }

    @Test
    void logoutRejectsMissingOrNonAdminAuthentication() {
        assertThatThrownBy(() -> adminAuthService.logout(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> adminAuthService.logout(new AuthUserInfo(ADMIN_PUBLIC_ID, "USER")))
                .isInstanceOf(BusinessException.class);
    }

    private void stubTokenIssue() {
        when(jwtTokenProvider.generateAccessToken(ADMIN_PUBLIC_ID, "ADMIN")).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(ADMIN_PUBLIC_ID)).thenReturn("refresh-token");
        when(jwtTokenProvider.getTokenType()).thenReturn("Bearer");
        when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(3600L);
    }
}
