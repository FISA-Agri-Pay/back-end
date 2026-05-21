package com.kkpp.batch.bss.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.kkpp.batch.bss.domain.LoanOverdueLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanOverdueLedgerRepository extends JpaRepository<LoanOverdueLedger, Long> {

    @Query("""
            SELECT l
            FROM LoanOverdueLedger l
            WHERE l.creditLimitId = :creditLimitId
              AND l.createdAt < :endDateExclusive
              AND (l.resolvedAt IS NULL OR l.resolvedAt >= :startDate)
            """)
    List<LoanOverdueLedger> findMonthlyOverdues(
            @Param("creditLimitId") Long creditLimitId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDateExclusive") LocalDateTime endDateExclusive
    );
}
