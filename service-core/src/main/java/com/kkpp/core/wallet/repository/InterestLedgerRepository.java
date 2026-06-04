package com.kkpp.core.wallet.repository;

import com.kkpp.core.wallet.domain.InterestLedger;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {

    Optional<InterestLedger> findFirstByCreditLimitPublicIdAndStatusInOrderByDueDateAsc(
            UUID creditLimitPublicId,
            Collection<String> statuses
    );
}
