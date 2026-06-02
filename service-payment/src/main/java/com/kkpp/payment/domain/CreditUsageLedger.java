package com.kkpp.payment.domain;

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

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "credit_limit_public_id", nullable = false)
    private UUID creditLimitPublicId;

    @Column(name = "order_public_id")
    private UUID orderPublicId;

    @Column(name = "payment_request_public_id")
    private UUID paymentRequestPublicId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String usageType;

    @Column(nullable = false)
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
        validateRequiredId(paymentRequestPublicId, "paymentRequestPublicId");
        validatePositiveAmount(amount);
        validateRequiredUsedAt(usedAt);

        CreditUsageLedger ledger = new CreditUsageLedger();
        ledger.publicId = UUID.randomUUID();
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
            throw new IllegalArgumentException(fieldName + "???꾩닔?낅땲??");
        }
    }

    private static void validatePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("?ъ슜 ?먯옣 湲덉븸? ?꾩닔?낅땲??");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("?ъ슜 ?먯옣 湲덉븸? 0蹂대떎 而ㅼ빞 ?⑸땲?? amount=" + amount);
        }
    }

    private static void validateRequiredUsedAt(LocalDateTime usedAt) {
        if (usedAt == null) {
            throw new IllegalArgumentException("?ъ슜 ?쇱떆???꾩닔?낅땲??");
        }
    }
}

