package com.kkpp.auth.service;

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
}
