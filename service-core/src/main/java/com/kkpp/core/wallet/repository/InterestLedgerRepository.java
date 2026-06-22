package com.kkpp.core.wallet.repository;

import com.kkpp.core.wallet.domain.InterestLedger;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {

    @Query(value = """
            SELECT *
            FROM core.interest_ledger
            WHERE credit_limit_public_id = :creditLimitPublicId
              AND status IN (:statuses)
            ORDER BY
              CASE WHEN due_date >= CURRENT_DATE THEN 0 ELSE 1 END ASC,
              CASE WHEN due_date >= CURRENT_DATE THEN due_date END ASC,
              CASE WHEN due_date < CURRENT_DATE THEN due_date END DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<InterestLedger> findNearestUnpaidLedger(
            @Param("creditLimitPublicId") UUID creditLimitPublicId,
            @Param("statuses") Collection<String> statuses
    );
}
