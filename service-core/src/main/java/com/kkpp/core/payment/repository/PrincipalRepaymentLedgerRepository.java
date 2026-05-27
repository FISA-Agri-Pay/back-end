package com.kkpp.core.payment.repository;

import com.kkpp.core.payment.domain.PrincipalRepaymentLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepaymentLedgerRepository extends JpaRepository<PrincipalRepaymentLedger, Long> {
}
