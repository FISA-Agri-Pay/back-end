package com.kkpp.core.payment.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "interest_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creditLimitId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrincipal;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interestAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid;

    private LocalDateTime paidAt;

    @Column(nullable = false, length = 20)
    private String status;

    public static InterestLedger upcoming(
            Long creditLimitId,
            BigDecimal basePrincipal,
            LocalDate dueDate,
            BigDecimal interestAmount
    ) {
        InterestLedger ledger = new InterestLedger();
        ledger.creditLimitId = creditLimitId;
        ledger.basePrincipal = basePrincipal;
        ledger.dueDate = dueDate;
        ledger.interestAmount = interestAmount;
        ledger.amountPaid = BigDecimal.ZERO;
        ledger.status = "UPCOMING";
        return ledger;
    }
}
