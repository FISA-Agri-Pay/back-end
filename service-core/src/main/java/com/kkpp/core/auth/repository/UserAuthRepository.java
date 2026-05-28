package com.kkpp.core.auth.repository;

import com.kkpp.core.auth.domain.User;
import com.kkpp.core.auth.domain.UserAuth;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthRepository extends JpaRepository<UserAuth, Long> {

    Optional<UserAuth> findByUser(User user);

    Optional<UserAuth> findByRefreshToken(String refreshToken);
}