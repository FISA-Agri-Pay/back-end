package com.kkpp.batch.principal.repayment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

@Entity(name = "PrincipalRepaymentCreditLimit")
@Table(name = "credit_limits", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLimit extends BaseEntity {

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal usedAmount;

    @Column(nullable = false)
    private String status;

    public boolean hasUsedAmount() {
        return usedAmount != null && usedAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    // 실제 원금 상환 금액만큼 현재 사용 한도를 줄인다. 음수 한도는 허용하지 않는다.
    public void decreaseUsedAmount(BigDecimal paymentAmount) {
        BigDecimal moneyPaymentAmount = toMoney(paymentAmount);
        if (moneyPaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("한도 사용액 감소 금액은 0보다 커야 합니다.");
        }
        if (usedAmount == null || usedAmount.compareTo(moneyPaymentAmount) < 0) {
            throw new IllegalStateException("한도 사용액보다 큰 금액은 감소시킬 수 없습니다. creditLimitId=" + id);
        }

        usedAmount = toMoney(usedAmount.subtract(moneyPaymentAmount));
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
