package com.kkpp.catalog.paymentpin.repository;

import com.kkpp.catalog.paymentpin.domain.PaymentPinVerification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentPinVerificationRepository extends JpaRepository<PaymentPinVerification, Long> {

    boolean existsByEventIdOrVerificationId(UUID eventId, UUID verificationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select verification from PaymentPinVerification verification where verification.verificationId = :verificationId")
    Optional<PaymentPinVerification> findByVerificationIdForUpdate(@Param("verificationId") UUID verificationId);
}
