package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.PrincipalRepaymentLedger;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 원금 상환 원장 조회 Repository — BNPL 관리자 KPI 산정에 사용된다.
public interface PrincipalRepaymentLedgerRepository extends JpaRepository<PrincipalRepaymentLedger, Long> {

    // 이용 현황 KPI — 당월 회수 예정 원금 합계 (미납 잔액 기준)
    @Query("""
            SELECT COALESCE(SUM(prl.principalAmount - prl.amountPaid), 0)
            FROM PrincipalRepaymentLedger prl
            WHERE prl.status IN (com.kkpp.admin.bnpl.domain.LedgerStatus.UPCOMING,
                                 com.kkpp.admin.bnpl.domain.LedgerStatus.PARTIAL)
              AND prl.dueDate >= :startOfMonth
              AND prl.dueDate <= :endOfMonth
            """)
    BigDecimal sumScheduledRepaymentThisMonth(
            @Param("startOfMonth") LocalDate startOfMonth,
            @Param("endOfMonth") LocalDate endOfMonth
    );
}
