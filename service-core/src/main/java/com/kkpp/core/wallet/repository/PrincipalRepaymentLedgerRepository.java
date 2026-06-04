package com.kkpp.core.wallet.repository;

import com.kkpp.core.wallet.domain.PrincipalRepaymentLedger;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepaymentLedgerRepository extends JpaRepository<PrincipalRepaymentLedger, Long> {

    Optional<PrincipalRepaymentLedger> findFirstByCreditLimitPublicIdAndStatusInOrderByDueDateAsc(
            UUID creditLimitPublicId,
            Collection<String> statuses
    );
}
