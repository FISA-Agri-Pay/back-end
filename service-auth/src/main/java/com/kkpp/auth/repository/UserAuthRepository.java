package com.kkpp.auth.repository;

import com.kkpp.auth.domain.User;
import com.kkpp.auth.domain.UserAuth;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthRepository extends JpaRepository<UserAuth, Long> {

    Optional<UserAuth> findByUser(User user);

    Optional<UserAuth> findByRefreshToken(String refreshToken);
}
