package com.kkpp.auth.repository;

import com.kkpp.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByPublicId(UUID publicId);

    boolean existsByPhone(String phone);

    boolean existsByResidentIdHash(String residentIdHash);
}

