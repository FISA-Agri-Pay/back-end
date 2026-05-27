package com.kkpp.batch.interest.domain;

import java.math.BigDecimal;
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
        ledger.interestAmount = interestAmount;
        ledger.amountPaid = BigDecimal.ZERO.setScale(2);
        ledger.status = STATUS_UPCOMING;
        ledger.createdAt = now;
        ledger.updatedAt = now;
        return ledger;
    }
}
