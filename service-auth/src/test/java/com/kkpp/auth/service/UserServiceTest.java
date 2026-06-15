package com.kkpp.auth.service;

import static com.kkpp.auth.testsupport.AuthTestEntityFactory.USER_ID;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.domain.UserAuth;
import com.kkpp.auth.dto.request.UpdateUserProfileRequest;
import com.kkpp.auth.dto.request.WithdrawRequest;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import com.kkpp.auth.exception.UserNotFoundException;
import com.kkpp.auth.repository.UserAuthRepository;
import com.kkpp.auth.repository.UserRepository;
import com.kkpp.auth.testsupport.AuthTestEntityFactory;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthRepository userAuthRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userAuthRepository, passwordEncoder);
    }

    @Test
    void getUserProfileReturnsUserProfile() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        var response = userService.getUserProfile(USER_ID);

        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.phone()).isEqualTo("01012345678");
        assertThat(response.address()).isEqualTo("서울시 강남구");
        assertThat(response.addressDetail()).isEqualTo("101호");
        assertThat(response.zipCode()).isEqualTo("12345");
    }

    @Test
    void getUserProfileThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateUserProfileUpdatesAddressFields() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        var request = new UpdateUserProfileRequest("부산시 해운대구", "202호", "67890");

        var response = userService.updateUserProfile(USER_ID, request);

        assertThat(response.address()).isEqualTo("부산시 해운대구");
        assertThat(response.addressDetail()).isEqualTo("202호");
        assertThat(response.zipCode()).isEqualTo("67890");
        assertThat(user.getAddress()).isEqualTo("부산시 해운대구");
        assertThat(user.getAddressDetail()).isEqualTo("202호");
        assertThat(user.getZipCode()).isEqualTo("67890");
    }

    @Test
    void updateUserProfileThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        var request = new UpdateUserProfileRequest("부산시 해운대구", "202호", "67890");

        assertThatThrownBy(() -> userService.updateUserProfile(USER_ID, request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void withdrawDeactivatesUserAndClearsSessionWhenPasswordMatches() {
        User user = user();
        UserAuth userAuth = AuthTestEntityFactory.userAuth(user);
        userAuth.updateRefreshToken("refresh-hash");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("password12", "encoded-password")).thenReturn(true);

        userService.withdraw(USER_ID, new WithdrawRequest("password12"));

        assertThat(user.isActive()).isFalse();
        assertThat(userAuth.getRefreshToken()).isNull();
    }

    @Test
    void updateUserProfileThrowsWhenUserAlreadyWithdrawn() {
        User user = user();
        user.withdraw();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        var request = new UpdateUserProfileRequest("부산시 해운대구", "202호", "67890");

        assertThatThrownBy(() -> userService.updateUserProfile(USER_ID, request))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN));
    }

    @Test
    void withdrawThrowsWhenUserAlreadyWithdrawn() {
        User user = user();
        user.withdraw();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw(USER_ID, new WithdrawRequest("password12")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN));
    }

    @Test
    void withdrawThrowsWhenPasswordDoesNotMatch() {
        User user = user();
        UserAuth userAuth = AuthTestEntityFactory.userAuth(user);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userAuthRepository.findByUser(user)).thenReturn(Optional.of(userAuth));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.withdraw(USER_ID, new WithdrawRequest("wrong-password")))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.PASSWORD_MISMATCH));
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void withdrawThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(USER_ID, new WithdrawRequest("password12")))
                .isInstanceOf(UserNotFoundException.class);
    }
}
