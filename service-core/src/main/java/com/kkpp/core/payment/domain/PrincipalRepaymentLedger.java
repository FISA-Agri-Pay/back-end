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
        validateRequiredId(creditLimitId, "creditLimitId");
        validateRequiredDueDate(dueDate);
        validatePositiveAmount(principalAmount);

        PrincipalRepaymentLedger ledger = new PrincipalRepaymentLedger();
        ledger.creditLimitId = creditLimitId;
        ledger.dueDate = dueDate;
        ledger.principalAmount = principalAmount;
        ledger.amountPaid = BigDecimal.ZERO;
        ledger.status = "UPCOMING";
        return ledger;
    }

    private static void validateRequiredId(Long id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
    }

    private static void validateRequiredDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("원금 상환 예정일은 필수입니다.");
        }
    }

    private static void validatePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("원금 상환 금액은 필수입니다.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("원금 상환 금액은 0보다 커야 합니다. amount=" + amount);
        }
    }
}
