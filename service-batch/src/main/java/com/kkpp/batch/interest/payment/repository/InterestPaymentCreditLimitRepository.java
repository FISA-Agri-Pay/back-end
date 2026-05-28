package com.kkpp.batch.interest.payment.repository;

import java.util.Optional;

import com.kkpp.batch.interest.domain.CreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("interestPaymentCreditLimitRepository")
public interface InterestPaymentCreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    Optional<CreditLimit> findById(Long id);
}
