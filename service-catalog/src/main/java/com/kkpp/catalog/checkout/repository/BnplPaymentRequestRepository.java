package com.kkpp.catalog.checkout.repository;

import com.kkpp.catalog.checkout.domain.BnplPaymentRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BnplPaymentRequestRepository extends JpaRepository<BnplPaymentRequest, Long> {

    Optional<BnplPaymentRequest> findByPublicIdAndUserPublicId(UUID publicId, UUID userPublicId);
}
