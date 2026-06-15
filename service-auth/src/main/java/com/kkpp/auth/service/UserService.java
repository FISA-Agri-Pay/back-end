package com.kkpp.auth.service;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.dto.request.UpdateUserProfileRequest;
import com.kkpp.auth.dto.response.UserProfileResponse;
import com.kkpp.auth.exception.UserNotFoundException;
import com.kkpp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getUserProfile(Long userId) {
        return userRepository.findById(userId)
                .map(UserProfileResponse::from)
                .orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public UserProfileResponse updateUserProfile(Long userId, UpdateUserProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.updateAddress(request.address(), request.addressDetail(), request.zipCode());
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.withdraw();
    }
}
