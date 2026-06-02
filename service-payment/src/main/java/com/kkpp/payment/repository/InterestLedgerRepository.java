package com.kkpp.payment.repository;

import com.kkpp.payment.domain.InterestLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {
}

