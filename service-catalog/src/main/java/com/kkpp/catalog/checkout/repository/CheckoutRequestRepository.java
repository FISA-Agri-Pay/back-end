package com.kkpp.catalog.checkout.repository;

import com.kkpp.catalog.checkout.domain.CheckoutRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutRequestRepository extends JpaRepository<CheckoutRequest, Long> {

    Optional<CheckoutRequest> findByPublicIdAndUserId(UUID publicId, Long userId);

    Optional<CheckoutRequest> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);
}
