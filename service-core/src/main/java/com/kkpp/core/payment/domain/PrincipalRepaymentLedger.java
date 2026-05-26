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
@Table(name = "principal_repayment_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrincipalRepaymentLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creditLimitId;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid;

    private LocalDateTime paidAt;

    @Column(nullable = false, length = 20)
    private String status;

    public static PrincipalRepaymentLedger upcoming(Long creditLimitId, LocalDate dueDate, BigDecimal principalAmount) {
        PrincipalRepaymentLedger ledger = new PrincipalRepaymentLedger();
        ledger.creditLimitId = creditLimitId;
        ledger.dueDate = dueDate;
        ledger.principalAmount = principalAmount;
        ledger.amountPaid = BigDecimal.ZERO;
        ledger.status = "UPCOMING";
        return ledger;
    }
}
