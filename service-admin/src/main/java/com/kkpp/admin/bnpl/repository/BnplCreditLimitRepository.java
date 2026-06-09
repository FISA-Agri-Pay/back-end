package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplCreditLimit;
import com.kkpp.admin.bnpl.dto.BnplUserSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// BNPL 관리자 화면에서 ERD 기준 core.credit_limits 테이블을 조회하는 Repository
public interface BnplCreditLimitRepository extends JpaRepository<BnplCreditLimit, Long> {

    @Query("""
            SELECT COUNT(cl)
            FROM BnplCreditLimit cl
            WHERE cl.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
              AND cl.id = (
                  SELECT MAX(latest.id)
                  FROM BnplCreditLimit latest
                  WHERE latest.userPublicId = cl.userPublicId
                    AND latest.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                          com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
              )
            """)
    long countCurrentBnplUsers();

    // 이용 현황 KPI — 전체 ACTIVE 한도의 사용 금액 합계
    @Query("""
            SELECT COALESCE(SUM(cl.usedAmount), 0)
            FROM BnplCreditLimit cl
            WHERE cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE
              AND cl.id = (
                  SELECT MAX(latest.id)
                  FROM BnplCreditLimit latest
                  WHERE latest.userPublicId = cl.userPublicId
              )
            """)
    BigDecimal sumActiveUsedAmount();

    @Query("""
            SELECT COUNT(cl)
            FROM BnplCreditLimit cl
            WHERE cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE
              AND cl.createdAt = (
                  SELECT MAX(latest.createdAt)
                  FROM BnplCreditLimit latest
                  WHERE latest.userPublicId = cl.userPublicId
                    AND latest.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                          com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
              )
              AND NOT EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                              WHERE lol.userPublicId = cl.userPublicId AND lol.resolvedAt IS NULL)
            """)
    long countNormalUsers();

    @Query("""
            SELECT COUNT(cl)
            FROM BnplCreditLimit cl
            WHERE cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED
              AND cl.createdAt = (
                  SELECT MAX(latest.createdAt)
                  FROM BnplCreditLimit latest
                  WHERE latest.userPublicId = cl.userPublicId
                    AND latest.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                          com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
              )
              AND NOT EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                              WHERE lol.userPublicId = cl.userPublicId AND lol.resolvedAt IS NULL)
            """)
    long countSuspendedUsers();

    // 사용자 publicId로 활성 한도 조회 — 단건 알림 발송 전 사용자 존재 확인용
    @Query("""
            SELECT cl
            FROM BnplCreditLimit cl
            WHERE cl.userPublicId = :userPublicId
              AND cl.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
              AND cl.id = (
                  SELECT MAX(latest.id)
                  FROM BnplCreditLimit latest
                  WHERE latest.userPublicId = cl.userPublicId
                    AND latest.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                          com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
              )
            """)
    Optional<BnplCreditLimit> findActiveByUserPublicId(@Param("userPublicId") UUID userPublicId);

    // 사용자별 BNPL 이용 현황 목록 — 연체 여부와 금액을 서브쿼리로 함께 조회한다.
    // search가 null이면 전체, 값이 있으면 이름·연락처 LIKE 검색, status는 ALL/NORMAL/OVERDUE로 필터링한다.
    @Query(
            value = """
                    SELECT new com.kkpp.admin.bnpl.dto.BnplUserSummaryResponse(
                        u.publicId,
                        u.name,
                        u.phone,
                        cl.totalLimit,
                        cl.usedAmount,
                        COALESCE((SELECT SUM(lol.overdueAmount) FROM LoanOverdueLedger lol
                                  WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL), 0),
                        cl.principalDueDate,
                        CASE WHEN EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                          WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL)
                             THEN true ELSE false END,
                        CASE WHEN EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                          WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL)
                             THEN 'OVERDUE'
                             WHEN cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED
                             THEN 'SUSPENDED'
                             ELSE 'NORMAL' END
                    )
                    FROM BnplCreditLimit cl, BnplUser u
                    WHERE cl.userPublicId = u.publicId
                      AND cl.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                        com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
                      AND cl.createdAt = (
                          SELECT MAX(latest.createdAt)
                          FROM BnplCreditLimit latest
                          WHERE latest.userPublicId = cl.userPublicId
                            AND latest.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                                  com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
                      )
                      AND (:search IS NULL OR u.name LIKE :searchPattern OR u.phone LIKE :searchPattern)
                      AND cl.createdAt >= :startDate
                      AND cl.createdAt <= :endDate
                      AND (:status = 'ALL'
                           OR (:status = 'NORMAL'
                               AND cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE
                               AND NOT EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                               WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL))
                           OR (:status = 'OVERDUE'
                               AND EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                           WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL))
                           OR (:status = 'SUSPENDED'
                               AND cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED
                               AND NOT EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                               WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL)))
                    ORDER BY u.name ASC
                    """,
            countQuery = """
                    SELECT COUNT(cl)
                    FROM BnplCreditLimit cl, BnplUser u
                    WHERE cl.userPublicId = u.publicId
                      AND cl.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                        com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
                      AND cl.createdAt = (
                          SELECT MAX(latest.createdAt)
                          FROM BnplCreditLimit latest
                          WHERE latest.userPublicId = cl.userPublicId
                            AND latest.status IN (com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE,
                                                  com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED)
                      )
                      AND (:search IS NULL OR u.name LIKE :searchPattern OR u.phone LIKE :searchPattern)
                      AND cl.createdAt >= :startDate
                      AND cl.createdAt <= :endDate
                      AND (:status = 'ALL'
                           OR (:status = 'NORMAL'
                               AND cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.ACTIVE
                               AND NOT EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                               WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL))
                           OR (:status = 'OVERDUE'
                               AND EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                           WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL))
                           OR (:status = 'SUSPENDED'
                               AND cl.status = com.kkpp.admin.bnpl.domain.BnplCreditLimitStatus.SUSPENDED
                               AND NOT EXISTS (SELECT 1 FROM LoanOverdueLedger lol
                                               WHERE lol.userPublicId = u.publicId AND lol.resolvedAt IS NULL)))
                    """
    )
    Page<BnplUserSummaryResponse> findBnplUsers(
            @Param("search") String search,
            @Param("searchPattern") String searchPattern,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") String status,
            Pageable pageable
    );
}
