package com.kkpp.payment.domain;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "principal_repayment_ledger", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrincipalRepaymentLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "credit_limit_public_id", nullable = false)
    private UUID creditLimitPublicId;

    @Column(name = "order_public_id", nullable = false, unique = true)
    private UUID orderPublicId;

    @Column(name = "payment_request_public_id")
    private UUID paymentRequestPublicId;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid;

    private LocalDateTime paidAt;

    @Column(nullable = false, length = 20)
    private String status;

    public static PrincipalRepaymentLedger upcoming(
            UUID creditLimitPublicId,
            UUID orderPublicId,
            UUID paymentRequestPublicId,
            LocalDate dueDate,
            BigDecimal principalAmount
    ) {
        validateRequiredId(creditLimitPublicId, "creditLimitPublicId");
        validateRequiredId(orderPublicId, "orderPublicId");
        validateRequiredDueDate(dueDate);
        validatePositiveAmount(principalAmount);

        PrincipalRepaymentLedger ledger = new PrincipalRepaymentLedger();
        ledger.publicId = UUID.randomUUID();
        ledger.creditLimitPublicId = creditLimitPublicId;
        ledger.orderPublicId = orderPublicId;
        ledger.paymentRequestPublicId = paymentRequestPublicId;
        ledger.dueDate = dueDate;
        ledger.principalAmount = principalAmount;
        ledger.amountPaid = BigDecimal.ZERO;
        ledger.status = "UPCOMING";
        return ledger;
    }

    private static void validateRequiredId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
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
