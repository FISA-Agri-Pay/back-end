package com.kkpp.core.payment.domain;

import com.kkpp.common.core.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "credit_usage_ledger", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditUsageLedger extends BaseTimeEntity {

    private static final String PURCHASE = "PURCHASE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_limit_public_id", nullable = false)
    private UUID creditLimitPublicId;

    @Column(name = "order_public_id")
    private UUID orderPublicId;

    @Column(name = "payment_request_public_id")
    private UUID paymentRequestPublicId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "usage_type", nullable = false, length = 20)
    private String usageType;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public static CreditUsageLedger purchase(
            UUID creditLimitPublicId,
            UUID orderPublicId,
            UUID paymentRequestPublicId,
            BigDecimal amount,
            LocalDateTime usedAt
    ) {
        validateRequiredId(creditLimitPublicId, "creditLimitPublicId");
        validateRequiredId(orderPublicId, "orderPublicId");
        validatePositiveAmount(amount);
        validateRequiredUsedAt(usedAt);

        CreditUsageLedger ledger = new CreditUsageLedger();
        ledger.creditLimitPublicId = creditLimitPublicId;
        ledger.orderPublicId = orderPublicId;
        ledger.paymentRequestPublicId = paymentRequestPublicId;
        ledger.amount = amount;
        ledger.usageType = PURCHASE;
        ledger.usedAt = usedAt;
        return ledger;
    }

    private static void validateRequiredId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
    }

    private static void validatePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("사용 원장 금액은 필수입니다.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("사용 원장 금액은 0보다 커야 합니다. amount=" + amount);
        }
    }

    private static void validateRequiredUsedAt(LocalDateTime usedAt) {
        if (usedAt == null) {
            throw new IllegalArgumentException("사용 일시는 필수입니다.");
        }
    }
}
