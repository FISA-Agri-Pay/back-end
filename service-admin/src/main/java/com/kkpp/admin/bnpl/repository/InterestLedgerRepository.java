package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.InterestLedger;
import com.kkpp.admin.bnpl.domain.LedgerStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 이자 원장 조회 Repository — BNPL 관리자 KPI 산정에 사용된다.
public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {

    // 이용 현황 KPI — 당월 회수 예정 이자 합계 (미납 잔액 기준)
    @Query("""
            SELECT COALESCE(SUM(il.interestAmount - il.amountPaid), 0)
            FROM InterestLedger il
            WHERE il.status IN (com.kkpp.admin.bnpl.domain.LedgerStatus.UPCOMING,
                                com.kkpp.admin.bnpl.domain.LedgerStatus.PARTIAL)
              AND il.dueDate >= :startOfMonth
              AND il.dueDate <= :endOfMonth
            """)
    BigDecimal sumScheduledRepaymentThisMonth(
            @Param("startOfMonth") LocalDate startOfMonth,
            @Param("endOfMonth") LocalDate endOfMonth
    );
}
