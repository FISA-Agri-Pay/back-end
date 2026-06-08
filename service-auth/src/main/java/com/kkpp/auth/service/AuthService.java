package com.kkpp.auth.service;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.domain.UserAuth;
import com.kkpp.auth.dto.request.LoginRequest;
import com.kkpp.auth.dto.request.RefreshTokenRequest;
import com.kkpp.auth.dto.request.RegisterRequest;
import com.kkpp.auth.dto.request.SetPaymentPinRequest;
import com.kkpp.auth.dto.response.TokenResponse;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import com.kkpp.auth.exception.UserAlreadyExistsException;
import com.kkpp.auth.exception.UserNotFoundException;
import com.kkpp.auth.repository.UserAuthRepository;
import com.kkpp.auth.repository.UserRepository;
import com.kkpp.common.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ResidentCryptoService residentCryptoService;

    @Transactional
    public void register(RegisterRequest request) {
        String normalizedPhone = normalizePhone(request.phone());
        if (userRepository.existsByPhone(normalizedPhone)) {
            log.warn("이미 가입된 휴대폰 번호로 회원가입을 시도했습니다.");
            throw new UserAlreadyExistsException();
        }

        String normalizedResidentId = residentCryptoService.normalize(request.residentId());
        String residentIdHash = residentCryptoService.hmac(normalizedResidentId);
        if (residentIdHash != null && userRepository.existsByResidentIdHash(residentIdHash)) {
            log.warn("이미 가입된 주민등록번호로 회원가입을 시도했습니다.");
            throw new UserAlreadyExistsException();
        }

        String residentIdEnc = residentCryptoService.encrypt(normalizedResidentId);
        User user = userRepository.save(User.create(
                request.name(),
                normalizedPhone,
                residentIdHash,
                residentIdEnc,
                request.address(),
                request.addressDetail(),
                request.zipCode()
        ));

        userAuthRepository.save(UserAuth.create(user, passwordEncoder.encode(request.password())));
        log.info("회원가입이 완료되었습니다. userId={}", user.getId());
    }

    @Transactional
    public void setPaymentPin(Long userId, SetPaymentPinRequest request) {
        UserAuth userAuth = getUserAuth(userId);
        userAuth.updatePin(passwordEncoder.encode(request.pin()));
        log.info("결제 PIN 등록이 완료되었습니다. userId={}", userId);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedPhone = normalizePhone(request.phone());
        User user = userRepository.findByPhone(normalizedPhone)
                .orElse(null);
        if (user == null) {
            log.warn("가입되지 않은 휴대폰 번호로 로그인을 시도했습니다. phone={}", maskPhone(normalizedPhone));
            throw new AuthException(AuthErrorCode.LOGIN_FAILED);
        }

        UserAuth userAuth = userAuthRepository.findByUser(user)
                .orElse(null);
        if (userAuth == null) {
            log.warn("인증 정보가 없는 사용자로 로그인을 시도했습니다. userId={}", user.getId());
            throw new AuthException(AuthErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.password(), userAuth.getPasswordHash())) {
            log.warn("비밀번호 불일치로 로그인이 실패했습니다. userId={}", user.getId());
            throw new AuthException(AuthErrorCode.LOGIN_FAILED);
        }

        userAuth.recordLogin();
        TokenResponse tokenResponse = issueTokens(userAuth);
        log.info("로그인이 완료되었습니다. userId={}", user.getId());
        return tokenResponse;
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        try {
            jwtTokenProvider.validateRefreshToken(request.refreshToken());
        } catch (IllegalArgumentException e) {
            log.warn("유효하지 않은 refresh token으로 토큰 재발급을 시도했습니다.");
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserAuth userAuth = userAuthRepository.findByRefreshToken(hashToken(request.refreshToken()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        TokenResponse tokenResponse = issueTokens(userAuth);
        log.info("토큰 재발급이 완료되었습니다. userId={}", userAuth.getUser().getId());
        return tokenResponse;
    }

    private TokenResponse issueTokens(UserAuth userAuth) {
        Long userId = userAuth.getUser().getId();
        UUID publicId = userAuth.getUser().getPublicId();

        String accessToken = jwtTokenProvider.generateUserAccessToken(userId, publicId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);
        userAuth.updateRefreshToken(hashToken(refreshToken));

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtTokenProvider.getTokenType(),
                jwtTokenProvider.getAccessTokenExpirySeconds(),
                userAuth.isPinSet()
        );
    }

    private UserAuth getUserAuth(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return userAuthRepository.findByUser(user)
                .orElseThrow(UserNotFoundException::new);
    }

    private String normalizePhone(String phone) {
        return phone.replace("-", "");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 알고리즘을 사용할 수 없습니다.", e);
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
