package com.kkpp.core.auth.service;

import com.kkpp.common.security.jwt.JwtTokenProvider;
import com.kkpp.core.auth.domain.User;
import com.kkpp.core.auth.domain.UserAuth;
import com.kkpp.core.auth.dto.request.LoginRequest;
import com.kkpp.core.auth.dto.request.RefreshTokenRequest;
import com.kkpp.core.auth.dto.request.RegisterRequest;
import com.kkpp.core.auth.dto.request.SetPaymentPinRequest;
import com.kkpp.core.auth.dto.response.TokenResponse;
import com.kkpp.core.auth.exception.AuthErrorCode;
import com.kkpp.core.auth.exception.AuthException;
import com.kkpp.core.auth.exception.InvalidPasswordException;
import com.kkpp.core.auth.exception.UserAlreadyExistsException;
import com.kkpp.core.auth.exception.UserNotFoundException;
import com.kkpp.core.auth.repository.UserAuthRepository;
import com.kkpp.core.auth.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw new UserAlreadyExistsException();
        }

        String normalizedResidentId = residentCryptoService.normalize(request.residentId());
        String residentIdHash = residentCryptoService.hmac(normalizedResidentId);
        if (residentIdHash != null && userRepository.existsByResidentIdHash(residentIdHash)) {
            throw new UserAlreadyExistsException();
        }

        String residentIdEnc = residentCryptoService.encrypt(normalizedResidentId);
        User user = userRepository.save(User.create(
                request.name(),
                request.phone(),
                residentIdHash,
                residentIdEnc,
                request.address(),
                request.addressDetail(),
                request.zipCode()
        ));

        UserAuth userAuth = userAuthRepository.save(
                UserAuth.create(user, passwordEncoder.encode(request.password()))
        );

        return issueTokens(userAuth);
    }

    @Transactional
    public void setPaymentPin(Long userId, SetPaymentPinRequest request) {
        UserAuth userAuth = getUserAuth(userId);
        userAuth.updatePin(passwordEncoder.encode(request.pin()));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByPhone(request.phone())
                .orElseThrow(UserNotFoundException::new);

        UserAuth userAuth = userAuthRepository.findByUser(user)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.password(), userAuth.getPasswordHash())) {
            throw new InvalidPasswordException();
        }

        userAuth.recordLogin();
        return issueTokens(userAuth);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        try {
            jwtTokenProvider.validateRefreshToken(request.refreshToken());
        } catch (IllegalArgumentException e) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserAuth userAuth = userAuthRepository.findByRefreshToken(hashToken(request.refreshToken()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(userAuth);
    }

    private TokenResponse issueTokens(UserAuth userAuth) {
        Long userId = userAuth.getUser().getId();

        String accessToken = jwtTokenProvider.generateUserAccessToken(userId);
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

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("[Auth] SHA-256 알고리즘을 사용할 수 없습니다", e);
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}