package com.kkpp.batch.overdue.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "OverdueDetectionLoanOverdueLedger")
@Table(name = "loan_overdue_ledger", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanOverdueLedger extends BaseEntity {

    public static final String STAGE_ACTIVE = "ACTIVE";

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID publicId;

    @Column(nullable = false)
    private UUID userPublicId;

    @Column(nullable = false)
    private UUID creditLimitPublicId;

    private UUID interestLedgerPublicId;

    private UUID principalRepaymentPublicId;

    @Column(nullable = false)
    private String overdueType;

    @Column(nullable = false)
    private BigDecimal overdueAmount;

    @Column(nullable = false)
    private Integer overdueDays;

    private String stage;

    @Column(nullable = false)
    private BigDecimal penaltyRate;

    @Column(nullable = false)
    private BigDecimal penaltyAmount;

    private LocalDateTime resolvedAt;

    public static LoanOverdueLedger interestOverdue(
            UUID userPublicId,
            UUID creditLimitPublicId,
            UUID interestLedgerPublicId,
            BigDecimal overdueAmount,
            int overdueDays
    ) {
        LoanOverdueLedger ledger = new LoanOverdueLedger();
        ledger.publicId = UUID.randomUUID();
        ledger.userPublicId = userPublicId;
        ledger.creditLimitPublicId = creditLimitPublicId;
        ledger.interestLedgerPublicId = interestLedgerPublicId;
        ledger.overdueType = "INTEREST";
        ledger.overdueAmount = toMoney(overdueAmount);
        ledger.overdueDays = overdueDays;
        ledger.stage = STAGE_ACTIVE;
        ledger.penaltyRate = BigDecimal.ZERO;
        ledger.penaltyAmount = BigDecimal.ZERO.setScale(MONEY_SCALE);
        return ledger;
    }

    public static LoanOverdueLedger principalOverdue(
            UUID userPublicId,
            UUID creditLimitPublicId,
            UUID principalRepaymentPublicId,
            BigDecimal overdueAmount,
            int overdueDays
    ) {
        LoanOverdueLedger ledger = new LoanOverdueLedger();
        ledger.publicId = UUID.randomUUID();
        ledger.userPublicId = userPublicId;
        ledger.creditLimitPublicId = creditLimitPublicId;
        ledger.principalRepaymentPublicId = principalRepaymentPublicId;
        ledger.overdueType = "PRINCIPAL";
        ledger.overdueAmount = toMoney(overdueAmount);
        ledger.overdueDays = overdueDays;
        ledger.stage = STAGE_ACTIVE;
        ledger.penaltyRate = BigDecimal.ZERO;
        ledger.penaltyAmount = BigDecimal.ZERO.setScale(MONEY_SCALE);
        return ledger;
    }

    // 같은 원장에 대한 활성 연체 이력이 이미 있으면, 최신 미납 금액과 연체 일수만 갱신한다.
    public void updateActive(BigDecimal overdueAmount, int overdueDays) {
        if (resolvedAt != null) {
            throw new IllegalStateException("이미 해소된 연체 이력은 연체 감지 배치에서 갱신할 수 없습니다. overdueLedgerId=" + id);
        }
        this.overdueAmount = toMoney(overdueAmount);
        this.overdueDays = overdueDays;
        this.stage = STAGE_ACTIVE;
    }

    private static BigDecimal toMoney(BigDecimal value) {
        BigDecimal safeValue = value == null ? BigDecimal.ZERO : value;
        return safeValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
