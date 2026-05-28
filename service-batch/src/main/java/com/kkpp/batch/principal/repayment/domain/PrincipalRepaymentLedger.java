package com.kkpp.batch.principal.repayment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

@Entity(name = "PrincipalAutoPaymentLedger")
@Table(name = "principal_repayment_ledger", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrincipalRepaymentLedger extends BaseEntity {

    public static final String STATUS_UPCOMING = "UPCOMING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_PARTIAL = "PARTIAL";

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creditLimitId;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private BigDecimal principalAmount;

    @Column(nullable = false)
    private BigDecimal amountPaid;

    private LocalDateTime paidAt;

    @Column(nullable = false)
    private String status;

    // 원금 원장에서 아직 상환되지 않은 금액을 계산한다.
    public BigDecimal getUnpaidAmount() {
        BigDecimal unpaidAmount = defaultZero(principalAmount).subtract(defaultZero(amountPaid));
        if (unpaidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return toMoney(unpaidAmount);
    }

    // Reader가 읽은 뒤 상태가 바뀌었을 수 있으므로 서비스에서 최신 상태로 한 번 더 판단한다.
    public boolean isPayableOn(LocalDate today) {
        return !dueDate.isAfter(today)
                && isPayableStatus()
                && getUnpaidAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isPaid() {
        return STATUS_PAID.equals(status);
    }

    public boolean isOverdue() {
        return STATUS_OVERDUE.equals(status);
    }

    // 실제 지갑에서 차감된 금액만큼 원금 상환 원장에 반영하고 상태를 갱신한다.
    public void applyPayment(BigDecimal paymentAmount, LocalDate today, LocalDateTime paidAt) {
        BigDecimal moneyPaymentAmount = toMoney(paymentAmount);
        if (moneyPaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("원금 자동 상환 금액은 0보다 커야 합니다.");
        }

        boolean wasOverdue = isOverdue();
        amountPaid = toMoney(defaultZero(amountPaid).add(moneyPaymentAmount));

        if (amountPaid.compareTo(principalAmount) >= 0) {
            amountPaid = toMoney(principalAmount);
            status = STATUS_PAID;
            this.paidAt = paidAt;
        } else if (wasOverdue || dueDate.isBefore(today)) {
            status = STATUS_OVERDUE;
        } else {
            status = STATUS_PARTIAL;
        }

    }

    private boolean isPayableStatus() {
        return STATUS_UPCOMING.equals(status)
                || STATUS_PARTIAL.equals(status)
                || STATUS_OVERDUE.equals(status);
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return defaultZero(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
