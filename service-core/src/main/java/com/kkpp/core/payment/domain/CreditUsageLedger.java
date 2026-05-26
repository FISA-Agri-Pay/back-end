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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "credit_usage_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditUsageLedger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creditLimitId;

    private Long orderId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String usageType;

    @Column(nullable = false)
    private LocalDateTime usedAt;

    public static CreditUsageLedger purchase(Long creditLimitId, Long orderId, BigDecimal amount, LocalDateTime usedAt) {
        CreditUsageLedger ledger = new CreditUsageLedger();
        ledger.creditLimitId = creditLimitId;
        ledger.orderId = orderId;
        ledger.amount = amount;
        ledger.usageType = "PURCHASE";
        ledger.usedAt = usedAt;
        return ledger;
    }
}
