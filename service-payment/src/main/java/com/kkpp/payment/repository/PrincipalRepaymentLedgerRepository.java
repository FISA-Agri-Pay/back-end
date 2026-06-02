package com.kkpp.payment.repository;

import com.kkpp.payment.domain.PrincipalRepaymentLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepaymentLedgerRepository extends JpaRepository<PrincipalRepaymentLedger, Long> {
}

