package com.kkpp.batch.interest.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.interest.domain.CreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("interestPaymentCreditLimitRepository")
public interface InterestPaymentCreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    Optional<CreditLimit> findByPublicId(UUID publicId);
}
