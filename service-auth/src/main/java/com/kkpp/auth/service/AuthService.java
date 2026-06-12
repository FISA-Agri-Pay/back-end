package com.kkpp.auth.service;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.domain.UserAuth;
import com.kkpp.auth.dto.request.LoginRequest;
import com.kkpp.auth.dto.request.RefreshTokenRequest;
import com.kkpp.auth.dto.request.RegisterRequest;
import com.kkpp.auth.dto.request.SetPaymentPinRequest;
import com.kkpp.auth.dto.request.VerifyPaymentPinRequest;
import com.kkpp.auth.dto.response.PaymentPinVerificationResponse;
import com.kkpp.auth.dto.response.TokenResponse;
import com.kkpp.auth.event.PaymentPinVerifiedEvent;
import com.kkpp.auth.event.PaymentPinVerifiedEventPublisher;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import com.kkpp.auth.exception.UserAlreadyExistsException;
import com.kkpp.auth.exception.UserNotFoundException;
import com.kkpp.auth.global.logging.LogMaskingUtils;
import com.kkpp.auth.global.logging.LoggingTimeUtils;
import com.kkpp.auth.global.tracing.TracingSupport;
import com.kkpp.auth.repository.UserAuthRepository;
import com.kkpp.auth.repository.UserRepository;
import com.kkpp.common.security.jwt.JwtTokenProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    /*
     * 인증 도메인의 핵심 비즈니스 로그를 남기는 서비스입니다.
     * Controller AOP는 API 공통 흐름을, 이 클래스는 회원가입/로그인/PIN 검증의 상세 성공·실패 원인을 기록합니다.
     */
    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ResidentCryptoService residentCryptoService;
    private final PaymentPinVerifiedEventPublisher paymentPinVerifiedEventPublisher;
    private final TracingSupport tracingSupport;

    @Value("${payment-pin-verification.ttl-seconds:300}")
    private long paymentPinVerificationTtlSeconds;

    @Transactional
    public void register(RegisterRequest request) {
        String normalizedPhone = normalizePhone(request.phone());
        if (userRepository.existsByPhone(normalizedPhone)) {
            // 이미 가입된 휴대폰 번호로 회원가입을 시도할 때 발생하는 운영 경고 로그입니다.
            log.atWarn()
                    .addKeyValue("event", "auth.register.failed")
                    .addKeyValue("failureState", "DUPLICATED_PHONE")
                    .addKeyValue("phone", LogMaskingUtils.maskPhone(normalizedPhone))
                    .addKeyValue("errorCode", AuthErrorCode.USER_ALREADY_EXISTS.getCode())
                    .addKeyValue("errorMessage", AuthErrorCode.USER_ALREADY_EXISTS.getMessage())
                    .log("이미 가입된 휴대폰 번호로 회원가입을 시도했습니다.");
            throw new UserAlreadyExistsException();
        }

        String normalizedResidentId = residentCryptoService.normalize(request.residentId());
        String residentIdHash = residentCryptoService.hmac(normalizedResidentId);
        if (residentIdHash != null && userRepository.existsByResidentIdHash(residentIdHash)) {
            // 주민등록번호 원문 대신 HMAC 결과를 마스킹해 중복 가입 원인만 식별합니다.
            log.atWarn()
                    .addKeyValue("event", "auth.register.failed")
                    .addKeyValue("failureState", "DUPLICATED_RESIDENT_ID")
                    .addKeyValue("residentIdHash", LogMaskingUtils.maskIdentifier(residentIdHash))
                    .addKeyValue("errorCode", AuthErrorCode.USER_ALREADY_EXISTS.getCode())
                    .addKeyValue("errorMessage", AuthErrorCode.USER_ALREADY_EXISTS.getMessage())
                    .log("이미 가입된 주민등록번호로 회원가입을 시도했습니다.");
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
        // 회원가입 성공 로그입니다. 비밀번호와 주민등록번호 원문은 절대 남기지 않습니다.
        log.atInfo()
                .addKeyValue("event", "auth.register.completed")
                .addKeyValue("userId", user.getId())
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(user.getPublicId()))
                .addKeyValue("phone", LogMaskingUtils.maskPhone(normalizedPhone))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("회원가입이 완료되었습니다.");
    }

    @Transactional
    public void setPaymentPin(Long userId, SetPaymentPinRequest request) {
        UserAuth userAuth = getUserAuth(userId);
        userAuth.updatePin(passwordEncoder.encode(request.pin()));
        // 결제 PIN 등록 성공 로그입니다. PIN 원문과 해시 값은 기록하지 않습니다.
        log.atInfo()
                .addKeyValue("event", "auth.payment-pin.register.completed")
                .addKeyValue("userId", userId)
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userAuth.getUser().getPublicId()))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("결제 PIN 등록이 완료되었습니다.");
    }

    public PaymentPinVerificationResponse verifyPaymentPin(Long userId, VerifyPaymentPinRequest request) {
        long startedAtNanos = System.nanoTime();
        Span span = tracingSupport.startSpan("service-auth.payment-pin.verify");
        try (Scope ignored = span.makeCurrent()) {
            // PIN 검증은 결제 흐름 추적이 중요하므로 별도 custom span으로 감쌉니다.
            return verifyPaymentPinWithSpan(userId, request, startedAtNanos, span);
        } catch (RuntimeException exception) {
            tracingSupport.recordException(span, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private PaymentPinVerificationResponse verifyPaymentPinWithSpan(
            Long userId,
            VerifyPaymentPinRequest request,
            long startedAtNanos,
            Span span
    ) {
        span.setAttribute("kkpp.event", "auth.payment-pin.verify");
        span.setAttribute("kkpp.user.id", userId);

        // PIN 검증 시작 로그입니다. 입력 PIN은 기록하지 않고 사용자 ID만 흐름 식별용으로 남깁니다.
        log.atInfo()
                .addKeyValue("event", "auth.payment-pin.verify.started")
                .addKeyValue("userId", userId)
                .log("결제 PIN 검증을 시작했습니다.");

        UserAuth userAuth = getUserAuth(userId);
        String maskedUserPublicId = LogMaskingUtils.maskIdentifier(userAuth.getUser().getPublicId());
        span.setAttribute("kkpp.user.public_id.masked", maskedUserPublicId);

        if (!userAuth.isPinSet()) {
            // PIN 미등록 상태에서 검증을 요청한 경우입니다. 결제 진행 전 사용자 설정 상태를 확인할 수 있습니다.
            AuthErrorCode errorCode = AuthErrorCode.PAYMENT_PIN_NOT_REGISTERED;
            span.setAttribute("kkpp.result", "FAILED");
            span.setAttribute("kkpp.failure_state", "PIN_NOT_REGISTERED");
            log.atWarn()
                    .addKeyValue("event", "auth.payment-pin.verify.failed")
                    .addKeyValue("userId", userId)
                    .addKeyValue("userPublicId", maskedUserPublicId)
                    .addKeyValue("failureState", "PIN_NOT_REGISTERED")
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .log("결제 PIN이 등록되지 않아 검증에 실패했습니다.");
            throw new AuthException(errorCode);
        }

        if (!passwordEncoder.matches(request.pin(), userAuth.getPinHash())) {
            // 사용자가 잘못된 PIN을 입력한 경우입니다. 보안상 실제 입력 PIN과 저장된 해시는 남기지 않습니다.
            AuthErrorCode errorCode = AuthErrorCode.PAYMENT_PIN_MISMATCH;
            span.setAttribute("kkpp.result", "FAILED");
            span.setAttribute("kkpp.failure_state", "PIN_MISMATCH");
            log.atWarn()
                    .addKeyValue("event", "auth.payment-pin.verify.failed")
                    .addKeyValue("userId", userId)
                    .addKeyValue("userPublicId", maskedUserPublicId)
                    .addKeyValue("failureState", "PIN_MISMATCH")
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .log("결제 PIN 불일치로 검증에 실패했습니다.");
            throw new AuthException(errorCode);
        }

        Instant verifiedAt = Instant.now();
        Instant expiresAt = verifiedAt.plusSeconds(resolveVerificationTtlSeconds());
        UUID verificationId = UUID.randomUUID();
        PaymentPinVerifiedEvent event = new PaymentPinVerifiedEvent(
                UUID.randomUUID(),
                verificationId,
                userAuth.getUser().getPublicId(),
                verifiedAt,
                expiresAt,
                "PAYMENT_PIN"
        );

        try {
            // PIN 검증 성공 이후 service-payment로 이어질 수 있도록 검증 완료 이벤트를 발행합니다.
            paymentPinVerifiedEventPublisher.publish(event);
        } catch (RuntimeException exception) {
            // PIN은 맞았지만 이벤트 발행이 실패한 경우입니다. 결제 흐름 단절 지점이라 error 로그로 남깁니다.
            AuthErrorCode errorCode = AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED;
            span.setAttribute("kkpp.result", "FAILED");
            span.setAttribute("kkpp.failure_state", "EVENT_PUBLISH_FAILED");
            log.atError()
                    .addKeyValue("event", "auth.payment-pin.verify.event-publish.failed")
                    .addKeyValue("userId", userId)
                    .addKeyValue("userPublicId", maskedUserPublicId)
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(verificationId))
                    .addKeyValue("failureState", "EVENT_PUBLISH_FAILED")
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 이벤트 발행에 실패했습니다.");
            throw new AuthException(errorCode);
        }

        span.setAttribute("kkpp.result", "SUCCESS");
        span.setAttribute("kkpp.verification.id.masked", LogMaskingUtils.maskIdentifier(verificationId));
        span.setAttribute("kkpp.duration_ms", LoggingTimeUtils.elapsedMillis(startedAtNanos));
        // PIN 검증 최종 성공 로그입니다. verificationId는 결제 요청 추적용으로 마스킹해 남깁니다.
        log.atInfo()
                .addKeyValue("event", "auth.payment-pin.verify.completed")
                .addKeyValue("userId", userId)
                .addKeyValue("userPublicId", maskedUserPublicId)
                .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(verificationId))
                .addKeyValue("expiresAt", expiresAt)
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .addKeyValue("resultStatus", "VERIFIED")
                .log("결제 PIN 검증이 완료되었습니다.");

        return new PaymentPinVerificationResponse(verificationId, expiresAt);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedPhone = normalizePhone(request.phone());
        User user = userRepository.findByPhone(normalizedPhone)
                .orElse(null);
        if (user == null) {
            // 가입되지 않은 휴대폰 번호로 로그인 시도한 경우입니다. 휴대폰 번호는 마스킹합니다.
            log.atWarn()
                    .addKeyValue("event", "auth.login.failed")
                    .addKeyValue("phone", LogMaskingUtils.maskPhone(normalizedPhone))
                    .addKeyValue("failureState", "USER_NOT_FOUND")
                    .addKeyValue("errorCode", AuthErrorCode.LOGIN_FAILED.getCode())
                    .addKeyValue("errorMessage", AuthErrorCode.LOGIN_FAILED.getMessage())
                    .log("가입되지 않은 휴대폰 번호로 로그인을 시도했습니다.");
            throw new AuthException(AuthErrorCode.LOGIN_FAILED);
        }

        UserAuth userAuth = userAuthRepository.findByUser(user)
                .orElse(null);
        if (userAuth == null) {
            // 사용자 엔티티는 있지만 인증 정보가 없는 데이터 불일치 상황입니다.
            log.atWarn()
                    .addKeyValue("event", "auth.login.failed")
                    .addKeyValue("userId", user.getId())
                    .addKeyValue("failureState", "USER_AUTH_NOT_FOUND")
                    .addKeyValue("errorCode", AuthErrorCode.LOGIN_FAILED.getCode())
                    .addKeyValue("errorMessage", AuthErrorCode.LOGIN_FAILED.getMessage())
                    .log("인증 정보가 없는 사용자로 로그인을 시도했습니다.");
            throw new AuthException(AuthErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(request.password(), userAuth.getPasswordHash())) {
            // 비밀번호 불일치로 로그인에 실패한 경우입니다. 비밀번호 원문과 해시는 남기지 않습니다.
            log.atWarn()
                    .addKeyValue("event", "auth.login.failed")
                    .addKeyValue("userId", user.getId())
                    .addKeyValue("failureState", "PASSWORD_MISMATCH")
                    .addKeyValue("errorCode", AuthErrorCode.LOGIN_FAILED.getCode())
                    .addKeyValue("errorMessage", AuthErrorCode.LOGIN_FAILED.getMessage())
                    .log("비밀번호 불일치로 로그인이 실패했습니다.");
            throw new AuthException(AuthErrorCode.LOGIN_FAILED);
        }

        userAuth.recordLogin();
        TokenResponse tokenResponse = issueTokens(userAuth);
        // 로그인 성공 로그입니다. 토큰 원문은 응답에만 포함하고 로그에는 남기지 않습니다.
        log.atInfo()
                .addKeyValue("event", "auth.login.completed")
                .addKeyValue("userId", user.getId())
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(user.getPublicId()))
                .addKeyValue("isPinSet", userAuth.isPinSet())
                .addKeyValue("resultStatus", "SUCCESS")
                .log("로그인이 완료되었습니다.");
        return tokenResponse;
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        try {
            jwtTokenProvider.validateRefreshToken(request.refreshToken());
        } catch (IllegalArgumentException e) {
            // refresh token 검증 자체가 실패한 경우입니다. 토큰 원문은 로그에 남기지 않습니다.
            log.atWarn()
                    .addKeyValue("event", "auth.refresh.failed")
                    .addKeyValue("failureState", "INVALID_REFRESH_TOKEN")
                    .addKeyValue("errorCode", AuthErrorCode.INVALID_REFRESH_TOKEN.getCode())
                    .addKeyValue("errorMessage", AuthErrorCode.INVALID_REFRESH_TOKEN.getMessage())
                    .log("유효하지 않은 refresh token으로 토큰 재발급을 시도했습니다.");
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserAuth userAuth = userAuthRepository.findByRefreshToken(hashToken(request.refreshToken()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        TokenResponse tokenResponse = issueTokens(userAuth);
        // refresh token 재발급 성공 로그입니다. 새 토큰 원문은 로그에 남기지 않습니다.
        log.atInfo()
                .addKeyValue("event", "auth.refresh.completed")
                .addKeyValue("userId", userAuth.getUser().getId())
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userAuth.getUser().getPublicId()))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("토큰 재발급이 완료되었습니다.");
        return tokenResponse;
    }

    private TokenResponse issueTokens(UserAuth userAuth) {
        // 토큰 생성과 refresh token 해시 저장을 한곳에서 처리합니다. 토큰 원문은 이 메서드 밖 로그에 노출하지 않습니다.
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

    private long resolveVerificationTtlSeconds() {
        return paymentPinVerificationTtlSeconds > 0 ? paymentPinVerificationTtlSeconds : 300;
    }

    private String normalizePhone(String phone) {
        return phone.replace("-", "");
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JVM에서 SHA-256을 사용할 수 없는 비정상 환경입니다.
            log.atError()
                    .addKeyValue("event", "auth.token.hash.failed")
                    .addKeyValue("algorithm", "SHA-256")
                    .addKeyValue("failureState", "HASH_ALGORITHM_NOT_AVAILABLE")
                    .setCause(e)
                    .log("토큰 해시에 필요한 SHA-256 알고리즘을 사용할 수 없습니다.");
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
