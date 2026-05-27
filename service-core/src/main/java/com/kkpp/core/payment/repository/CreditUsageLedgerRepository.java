package com.kkpp.core.payment.repository;

import com.kkpp.core.payment.domain.CreditUsageLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditUsageLedgerRepository extends JpaRepository<CreditUsageLedger, Long> {

    boolean existsByOrderIdAndUsageType(Long orderId, String usageType);
}
