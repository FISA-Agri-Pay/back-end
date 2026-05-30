package com.kkpp.admin.bnpl.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "loan_overdue_ledger", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 연체 이력을 관리하는 loan_overdue_ledger 테이블 매핑 엔티티
// resolved_at IS NULL 조건으로 미해소 연체만 조회하는 것이 핵심이다.
// userPublicId, creditLimitPublicId 는 UUID 참조로 JPA 연관 없이 관리한다.
public class LoanOverdueLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "credit_limit_public_id", nullable = false)
    private UUID creditLimitPublicId;

    @Column(name = "interest_ledger_public_id")
    private UUID interestLedgerPublicId;

    @Column(name = "principal_repayment_public_id")
    private UUID principalRepaymentPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "overdue_type", nullable = false, length = 20)
    private OverdueType overdueType;

    @Column(name = "overdue_amount", precision = 15, scale = 2)
    private BigDecimal overdueAmount;

    @Column(name = "overdue_days")
    private Integer overdueDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OverdueStage stage;

    @Column(name = "penalty_rate", precision = 6, scale = 4)
    private BigDecimal penaltyRate;

    @Column(name = "penalty_amount", precision = 15, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    // NULL이면 미해소 연체, NOT NULL이면 해소된 연체
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
