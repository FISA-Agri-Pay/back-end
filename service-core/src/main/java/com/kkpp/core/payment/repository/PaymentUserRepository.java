package com.kkpp.core.payment.repository;

import com.kkpp.core.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);
}