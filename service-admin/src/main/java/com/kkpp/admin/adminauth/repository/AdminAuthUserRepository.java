package com.kkpp.admin.adminauth.repository;

import com.kkpp.admin.adminauth.domain.AdminAuthUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuthUserRepository extends JpaRepository<AdminAuthUser, Long> {

    Optional<AdminAuthUser> findByEmailIgnoreCase(String email);

    Optional<AdminAuthUser> findByRefreshToken(String refreshToken);
}
