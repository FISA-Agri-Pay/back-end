package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.LoanOverdueLedger;
import com.kkpp.admin.bnpl.domain.OverdueStage;
import com.kkpp.admin.bnpl.domain.OverdueType;
import com.kkpp.admin.bnpl.dto.OverdueUserSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 연체 이력 조회 Repository — 연체 현황 KPI, 목록, 일괄 알림 대상 조회를 담당한다.
// resolved_at IS NULL 조건으로 미해소 연체만 조회하는 것이 핵심이다.
public interface LoanOverdueLedgerRepository extends JpaRepository<LoanOverdueLedger, Long> {

    long countByResolvedAtIsNull();

    // 연체 현황 KPI — 미해소 연체 금액 합계
    @Query("SELECT COALESCE(SUM(lol.overdueAmount), 0) FROM LoanOverdueLedger lol WHERE lol.resolvedAt IS NULL")
    BigDecimal sumUnresolvedOverdueAmount();

    // 연체 현황 KPI — 미해소 연체 이자 합계
    @Query("SELECT COALESCE(SUM(lol.penaltyAmount), 0) FROM LoanOverdueLedger lol WHERE lol.resolvedAt IS NULL")
    BigDecimal sumUnresolvedPenaltyAmount();

    // 연체 현황 KPI — 연체 회원 수 (중복 제거)
    @Query("SELECT COUNT(DISTINCT lol.userPublicId) FROM LoanOverdueLedger lol WHERE lol.resolvedAt IS NULL")
    long countDistinctOverdueUsers();

    // 연체 현황 KPI — 단계별 연체 건수 집계
    // 반환값: Object[] { OverdueStage, Long }
    @Query("""
            SELECT lol.stage, COUNT(lol)
            FROM LoanOverdueLedger lol
            WHERE lol.resolvedAt IS NULL
            GROUP BY lol.stage
            """)
    List<Object[]> countByStage();

    // 연체 이용 현황 KPI용 — 이용 현황 페이지의 overdueAmount 계산에 사용
    @Query("SELECT COALESCE(SUM(lol.overdueAmount), 0) FROM LoanOverdueLedger lol WHERE lol.resolvedAt IS NULL")
    BigDecimal sumOverdueAmountForSummary();

    // 연체 대상자 목록 — 필터 조건(overdueType, stage, minDays, dueDate 날짜범위)으로 페이지 조회한다.
    // dueDate는 overdueType에 따라 interest_ledger 또는 principal_repayment_ledger에서 조합한다.
    // alertSentAt은 notifications 테이블의 마지막 발송 시각 서브쿼리로 채운다.
    @Query(
            value = """
                    SELECT new com.kkpp.admin.bnpl.dto.OverdueUserSummaryResponse(
                        u.publicId,
                        u.name,
                        u.phone,
                        lol.overdueType,
                        lol.overdueAmount,
                        lol.penaltyAmount,
                        lol.overdueDays,
                        lol.stage,
                        COALESCE(il.dueDate, prl.dueDate),
                        prl.orderPublicId,
                        prl.paymentRequestPublicId,
                        (SELECT MAX(n.createdAt) FROM BnplNotification n
                         WHERE n.userPublicId = lol.userPublicId
                           AND n.notificationType LIKE 'OVERDUE_ALERT_%')
                    )
                    FROM LoanOverdueLedger lol
                    JOIN BnplUser u ON u.publicId = lol.userPublicId
                    LEFT JOIN InterestLedger il ON il.publicId = lol.interestLedgerPublicId
                    LEFT JOIN PrincipalRepaymentLedger prl ON prl.publicId = lol.principalRepaymentPublicId
                    WHERE lol.resolvedAt IS NULL
                      AND (:overdueType IS NULL OR lol.overdueType = :overdueType)
                      AND (:stage IS NULL OR lol.stage = :stage)
                      AND (:minDays IS NULL OR lol.overdueDays >= :minDays)
                      AND COALESCE(il.dueDate, prl.dueDate) >= :startDate
                      AND COALESCE(il.dueDate, prl.dueDate) <= :endDate
                    ORDER BY lol.overdueDays DESC
                    """,
            countQuery = """
                    SELECT COUNT(lol)
                    FROM LoanOverdueLedger lol
                    JOIN BnplUser u ON u.publicId = lol.userPublicId
                    LEFT JOIN InterestLedger il ON il.publicId = lol.interestLedgerPublicId
                    LEFT JOIN PrincipalRepaymentLedger prl ON prl.publicId = lol.principalRepaymentPublicId
                    WHERE lol.resolvedAt IS NULL
                      AND (:overdueType IS NULL OR lol.overdueType = :overdueType)
                      AND (:stage IS NULL OR lol.stage = :stage)
                      AND (:minDays IS NULL OR lol.overdueDays >= :minDays)
                      AND COALESCE(il.dueDate, prl.dueDate) >= :startDate
                      AND COALESCE(il.dueDate, prl.dueDate) <= :endDate
                    """
    )
    Page<OverdueUserSummaryResponse> findOverdueUsers(
            @Param("overdueType") OverdueType overdueType,
            @Param("stage") OverdueStage stage,
            @Param("minDays") Integer minDays,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );

    // 연체 일괄 알림 발송 — userPublicIds 미입력 시 전체 미해소 연체자 UUID 목록 조회
    @Query("SELECT DISTINCT lol.userPublicId FROM LoanOverdueLedger lol WHERE lol.resolvedAt IS NULL")
    List<UUID> findUnresolvedUserPublicIds();
}
