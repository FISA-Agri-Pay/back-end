package com.kkpp.batch.interest.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "InterestChargeLedger")
@Table(name = "interest_ledger", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestLedger {

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
    private BigDecimal basePrincipal;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private BigDecimal interestAmount;

    @Column(nullable = false)
    private BigDecimal amountPaid;

    private LocalDateTime paidAt;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 새로 생성되는 월별 이자 원장은 아직 납부 전인 UPCOMING 상태로 시작한다.
    public static InterestLedger create(
            Long creditLimitId,
            BigDecimal basePrincipal,
            LocalDate dueDate,
            BigDecimal interestAmount,
            LocalDateTime now
    ) {
        InterestLedger ledger = new InterestLedger();
        ledger.creditLimitId = creditLimitId;
        ledger.basePrincipal = basePrincipal;
        ledger.dueDate = dueDate;
        ledger.interestAmount = toMoney(interestAmount);
        ledger.amountPaid = BigDecimal.ZERO.setScale(MONEY_SCALE);
        ledger.status = STATUS_UPCOMING;
        ledger.createdAt = now;
        ledger.updatedAt = now;
        return ledger;
    }

    // 현재 원장에 남아 있는 미납 이자 금액을 계산한다.
    // 자동 상환 배치는 이 값을 기준으로 지갑에서 얼마까지 차감할 수 있는지 판단한다.
    public BigDecimal getUnpaidAmount() {
        BigDecimal unpaidAmount = defaultZero(interestAmount).subtract(defaultZero(amountPaid));
        if (unpaidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return toMoney(unpaidAmount);
    }

    // 자동 상환 배치의 1차 처리 대상 여부를 판단한다.
    // 납부 예정일이 미래이거나, 이미 PAID이거나, 미납 금액이 없으면 처리하지 않는다.
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

    // 실제 차감된 금액만큼 납부 금액을 증가시키고 원장 상태를 갱신한다.
    // 전액 납부되면 PAID가 되고, 일부 납부만 된 경우에는 납부 시점과 기존 연체 여부에 따라 상태를 나눈다.
    public void applyPayment(BigDecimal paymentAmount, LocalDate today, LocalDateTime paidAt) {
        BigDecimal moneyPaymentAmount = toMoney(paymentAmount);
        if (moneyPaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("이자 자동 상환 금액은 0보다 커야 합니다.");
        }

        // 기존 OVERDUE 원장은 일부 납부 후에도 OVERDUE 상태를 유지해야 하므로 먼저 기억해둔다.
        boolean wasOverdue = isOverdue();
        amountPaid = toMoney(defaultZero(amountPaid).add(moneyPaymentAmount));

        // 초과 납부가 발생하지 않도록 원장 금액을 상한으로 맞춘다.
        if (amountPaid.compareTo(interestAmount) >= 0) {
            amountPaid = toMoney(interestAmount);
            status = STATUS_PAID;
            this.paidAt = paidAt;
        } else if (wasOverdue || dueDate.isBefore(today)) {
            // 납부일이 지난 뒤 미납이 남아 있으면 PARTIAL이 아니라 OVERDUE로 관리한다.
            status = STATUS_OVERDUE;
        } else {
            // 납부기한 내 일부 납부는 PARTIAL로 관리한다.
            status = STATUS_PARTIAL;
        }

        updatedAt = paidAt;
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
