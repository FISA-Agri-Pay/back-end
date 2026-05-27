package com.kkpp.core.payment.repository;

import com.kkpp.core.payment.domain.InterestLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {
}
