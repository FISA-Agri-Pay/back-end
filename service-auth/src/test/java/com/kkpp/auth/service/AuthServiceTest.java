package com.kkpp.auth.service;

import static com.kkpp.auth.testsupport.AuthTestEntityFactory.PHONE;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.USER_ID;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.user;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.userAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.domain.UserAuth;
import com.kkpp.auth.dto.request.LoginRequest;
import com.kkpp.auth.dto.request.RefreshTokenRequest;
import com.kkpp.auth.dto.request.RegisterRequest;
import com.kkpp.auth.dto.request.SetPaymentPinRequest;
import com.kkpp.auth.dto.request.VerifyPaymentPinRequest;
import com.kkpp.auth.event.PaymentPinVerifiedEvent;
import com.kkpp.auth.event.PaymentPinVerifiedEventPublisher;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import com.kkpp.auth.exception.UserAlreadyExistsException;
import com.kkpp.auth.exception.UserNotFoundException;
import com.kkpp.auth.global.tracing.TracingSupport;
import com.kkpp.auth.repository.UserAuthRepository;
import com.kkpp.auth.repository.UserRepository;
import com.kkpp.common.security.jwt.JwtTokenProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthRepository userAuthRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ResidentCryptoService residentCryptoService;

    @Mock
    private PaymentPinVerifiedEventPublisher paymentPinVerifiedEventPublisher;

    @Mock
    private TracingSupport tracingSupport;

    @Mock
    private Span span;

    @Mock
    private Scope scope;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                userAuthRepository,
                jwtTokenProvider,
                passwordEncoder,
                residentCryptoService,
                paymentPinVerifiedEventPublisher,
                tracingSupport
        );
        ReflectionTestUtils.setField(authService, "paymentPinVerificationTtlSeconds", 300L);
    }

    @Test
    void registerSavesUserAndAuthForNewUser() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        when(residentCryptoService.normalize("900101-1234567")).thenReturn("9001011234567");
        when(residentCryptoService.hmac("9001011234567")).thenReturn("v2$resident-hash");
        when(userRepository.existsByResidentIdHash("v2$resident-hash")).thenReturn(false);
        when(residentCryptoService.encrypt("9001011234567")).thenReturn("v2$resident-enc");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("password12")).thenReturn("encoded-password");
        when(userAuthRepository.save(any(UserAuth.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<UserAuth> authCaptor = ArgumentCaptor.forClass(UserAuth.class);
        verify(userRepository).save(userCaptor.capture());
        verify(userAuthRepository).save(authCaptor.capture());
        assertThat(userCaptor.getValue().getPhone()).isEqualTo(PHONE);
        assertThat(userCaptor.getValue().getResidentIdHash()).isEqualTo("v2$resident-hash");
        assertThat(authCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    void registerSkipsResidentDuplicateCheckWhenHashIsNull() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        when(residentCryptoService.normalize("900101-1234567")).thenReturn("9001011234567");
        when(residentCryptoService.hmac("9001011234567")).thenReturn(null);
        when(residentCryptoService.encrypt("9001011234567")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("password12")).thenReturn("encoded-password");

        authService.register(request);

        verify(userRepository, never()).existsByResidentIdHash(any());
        verify(userAuthRepository).save(any(UserAuth.class));
    }

    @Test
    void registerRejectsDuplicatedPhoneOrResidentId() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(UserAlreadyExistsException.class);

        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        when(residentCryptoService.normalize("900101-1234567")).thenReturn("9001011234567");
        when(residentCryptoService.hmac("9001011234567")).thenReturn("v2$resident-hash");
        when(userRepository.existsByResidentIdHash("v2$resident-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginIssuesTokensAndRecordsLogin() {
        User user = user();
        UserAuth userAuth = userAuth(user);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("password12", "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.generateUserAccessToken(USER_ID, USER_PUBLIC_ID)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn("refresh-token");
        when(jwtTokenProvider.getTokenType()).thenReturn("Bearer");
        when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(3600L);

        var response = authService.login(new LoginRequest("010-1234-5678", "password12"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(userAuth.getLastLoginAt()).isNotNull();
        assertThat(userAuth.getRefreshToken()).isEqualTo(sha256("refresh-token"));
    }

    @Test
    void loginRejectsUnknownUserMissingAuthAndWrongPassword() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest(PHONE, "password12")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED));

        User user = user();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest(PHONE, "password12")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED));

        UserAuth userAuth = userAuth(user);
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("bad-password", "encoded-password")).thenReturn(false);
        assertThatThrownBy(() -> authService.login(new LoginRequest(PHONE, "bad-password")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_FAILED));
    }

    @Test
    void refreshValidatesTokenAndIssuesNewTokens() {
        User user = user();
        UserAuth userAuth = userAuth(user);
        doNothing().when(jwtTokenProvider).validateRefreshToken("old-refresh-token");
        when(userAuthRepository.findByRefreshToken(sha256("old-refresh-token"))).thenReturn(Optional.of(userAuth));
        when(jwtTokenProvider.generateUserAccessToken(USER_ID, USER_PUBLIC_ID)).thenReturn("new-access-token");
        when(jwtTokenProvider.generateRefreshToken(USER_ID)).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getTokenType()).thenReturn("Bearer");
        when(jwtTokenProvider.getAccessTokenExpirySeconds()).thenReturn(3600L);

        var response = authService.refresh(new RefreshTokenRequest("old-refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(userAuth.getRefreshToken()).isEqualTo(sha256("new-refresh-token"));
    }

    @Test
    void refreshRejectsInvalidOrUnknownRefreshToken() {
        doThrow(new IllegalArgumentException("invalid"))
                .when(jwtTokenProvider).validateRefreshToken("invalid-refresh-token");

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("invalid-refresh-token")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

        doNothing().when(jwtTokenProvider).validateRefreshToken("unknown-refresh-token");
        when(userAuthRepository.findByRefreshToken(sha256("unknown-refresh-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("unknown-refresh-token")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void setPaymentPinStoresEncodedPin() {
        User user = user();
        UserAuth userAuth = userAuth(user);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.encode("123456")).thenReturn("encoded-pin");

        authService.setPaymentPin(USER_ID, new SetPaymentPinRequest("123456"));

        assertThat(userAuth.getPinHash()).isEqualTo("encoded-pin");
        assertThat(userAuth.getPinChangedAt()).isNotNull();
    }

    @Test
    void setPaymentPinRejectsMissingUserOrAuth() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.setPaymentPin(USER_ID, new SetPaymentPinRequest("123456")))
                .isInstanceOf(UserNotFoundException.class);

        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.setPaymentPin(USER_ID, new SetPaymentPinRequest("123456")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void verifyPaymentPinPublishesEventAndReturnsVerificationWindow() {
        User user = user();
        UserAuth userAuth = userAuth(user);
        userAuth.updatePin("encoded-pin");
        mockTracing();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("123456", "encoded-pin")).thenReturn(true);

        var response = authService.verifyPaymentPin(USER_ID, new VerifyPaymentPinRequest("123456"));

        assertThat(response.verificationId()).isNotNull();
        assertThat(response.expiresAt()).isNotNull();
        ArgumentCaptor<PaymentPinVerifiedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentPinVerifiedEvent.class);
        verify(paymentPinVerifiedEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(eventCaptor.getValue().verificationType()).isEqualTo("PAYMENT_PIN");
        verify(span).end();
    }

    @Test
    void verifyPaymentPinUsesDefaultTtlWhenConfiguredValueIsInvalid() {
        ReflectionTestUtils.setField(authService, "paymentPinVerificationTtlSeconds", 0L);
        User user = user();
        UserAuth userAuth = userAuth(user);
        userAuth.updatePin("encoded-pin");
        mockTracing();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("123456", "encoded-pin")).thenReturn(true);

        var response = authService.verifyPaymentPin(USER_ID, new VerifyPaymentPinRequest("123456"));

        assertThat(response.expiresAt()).isAfter(java.time.Instant.now().plusSeconds(250));
    }

    @Test
    void verifyPaymentPinRejectsNotRegisteredOrMismatchedPin() {
        User user = user();
        UserAuth userAuth = userAuth(user);
        mockTracing();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));

        assertThatThrownBy(() -> authService.verifyPaymentPin(USER_ID, new VerifyPaymentPinRequest("123456")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PAYMENT_PIN_NOT_REGISTERED));

        userAuth.updatePin("encoded-pin");
        when(passwordEncoder.matches("000000", "encoded-pin")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyPaymentPin(USER_ID, new VerifyPaymentPinRequest("000000")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PAYMENT_PIN_MISMATCH));
        verify(paymentPinVerifiedEventPublisher, never()).publish(any());
    }

    @Test
    void verifyPaymentPinWrapsEventPublishFailure() {
        User user = user();
        UserAuth userAuth = userAuth(user);
        userAuth.updatePin("encoded-pin");
        mockTracing();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("123456", "encoded-pin")).thenReturn(true);
        doThrow(new RuntimeException("sqs down")).when(paymentPinVerifiedEventPublisher).publish(any());

        assertThatThrownBy(() -> authService.verifyPaymentPin(USER_ID, new VerifyPaymentPinRequest("123456")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED));
        verify(tracingSupport, atLeastOnce()).recordException(eq(span), any(RuntimeException.class));
    }

    private RegisterRequest registerRequest() {
        return new RegisterRequest(
                "010-1234-5678",
                "홍길동",
                "서울시 강남구",
                "101호",
                "12345",
                "900101-1234567",
                "password12"
        );
    }

    private void mockTracing() {
        when(tracingSupport.startSpan("service-auth.payment-pin.verify")).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);
    }

    private String sha256(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
