package com.kkpp.core.payment.repository;

import com.kkpp.core.payment.domain.CreditUsageLedger;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditUsageLedgerRepository extends JpaRepository<CreditUsageLedger, Long> {

    boolean existsByOrderPublicIdAndUsageType(UUID orderPublicId, String usageType);
}
