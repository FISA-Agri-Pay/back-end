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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${resident-id.hmac-key}")
    private String residentIdHmacKey;

    @Value("${resident-id.encryption-key}")
    private String residentIdEncryptionKey;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw new UserAlreadyExistsException();
        }

        String normalizedResidentId = normalizeResidentId(request.residentId());
        String residentIdHash = hmacResidentId(normalizedResidentId);
        if (residentIdHash != null && userRepository.existsByResidentIdHash(residentIdHash)) {
            throw new UserAlreadyExistsException();
        }

        String residentIdEnc = encryptResidentId(normalizedResidentId);
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

        String accessToken = jwtTokenProvider.generateAccessToken(userId, "USER");
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

    private String normalizeResidentId(String residentId) {
        if (!StringUtils.hasText(residentId)) {
            return residentId;
        }
        return residentId.replace("-", "");
    }

    private String encryptResidentId(String residentId) {
        if (!StringUtils.hasText(residentId)) {
            return null;
        }
        try {
            SecretKeySpec key = deriveAesKey();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(residentId.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return "v2$" + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Resident ID encryption failed.", e);
        }
    }

    // resident_id 복호화 함수 (실제로 복호화해서 회원 조회할 때 사용 예정)
    public String decryptResidentId(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        if (!encrypted.startsWith("v2$")) {
            throw new IllegalArgumentException("Unknown resident ID encryption format.");
        }
        try {
            SecretKeySpec key = deriveAesKey();
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(3));
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Resident ID decryption failed.", e);
        }
    }

    private String hmacResidentId(String residentId) {
        if (!StringUtils.hasText(residentId)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(residentIdHmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(residentId.getBytes(StandardCharsets.UTF_8));
            return "v2$" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable.", e);
        }
    }

    private SecretKeySpec deriveAesKey() throws NoSuchAlgorithmException {
        byte[] raw = MessageDigest.getInstance("SHA-256")
                .digest(residentIdEncryptionKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(raw, "AES");
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable.", e);
        }
    }
}
