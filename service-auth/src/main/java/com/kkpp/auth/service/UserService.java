package com.kkpp.auth.service;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.domain.UserAuth;
import com.kkpp.auth.dto.request.UpdateUserProfileRequest;
import com.kkpp.auth.dto.request.WithdrawRequest;
import com.kkpp.auth.dto.response.UserProfileResponse;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import com.kkpp.auth.exception.UserNotFoundException;
import com.kkpp.auth.global.logging.LogMaskingUtils;
import com.kkpp.auth.repository.UserAuthRepository;
import com.kkpp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserAuthRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;

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
    public void withdraw(Long userId, WithdrawRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        UserAuth userAuth = userAuthRepository.findByUser(user)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.password(), userAuth.getPasswordHash())) {
            // 탈퇴 확인용 비밀번호가 일치하지 않는 경우입니다. 비밀번호 원문과 해시는 남기지 않습니다.
            log.atWarn()
                    .addKeyValue("event", "auth.withdraw.failed")
                    .addKeyValue("userId", userId)
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(user.getPublicId()))
                    .addKeyValue("failureState", "PASSWORD_MISMATCH")
                    .addKeyValue("errorCode", AuthErrorCode.PASSWORD_MISMATCH.getCode())
                    .addKeyValue("errorMessage", AuthErrorCode.PASSWORD_MISMATCH.getMessage())
                    .log("비밀번호 불일치로 회원 탈퇴에 실패했습니다.");
            throw new AuthException(AuthErrorCode.PASSWORD_MISMATCH);
        }

        user.withdraw();
        // 회원 탈퇴(Soft Delete) 성공 로그입니다.
        log.atInfo()
                .addKeyValue("event", "auth.withdraw.completed")
                .addKeyValue("userId", userId)
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(user.getPublicId()))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("회원 탈퇴가 완료되었습니다.");
    }
}
