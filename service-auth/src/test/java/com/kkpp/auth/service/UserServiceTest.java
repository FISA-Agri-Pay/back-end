package com.kkpp.auth.service;

import static com.kkpp.auth.testsupport.AuthTestEntityFactory.USER_ID;
import static com.kkpp.auth.testsupport.AuthTestEntityFactory.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.dto.request.UpdateUserProfileRequest;
import com.kkpp.auth.exception.UserNotFoundException;
import com.kkpp.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
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
    void withdrawDeactivatesUser() {
        User user = user();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.withdraw(USER_ID);

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void withdrawThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }
}
