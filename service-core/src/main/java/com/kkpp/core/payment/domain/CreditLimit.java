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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "credit_limits", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLimit extends BaseEntity {

    private static final String ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalLimit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal usedAmount;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private LocalDate principalDueDate;

    private LocalDate expiresAt;

    @Column(nullable = false, length = 20)
    private String status;

    public boolean isActive(LocalDate today) {
        return ACTIVE.equals(status) && (expiresAt == null || !expiresAt.isBefore(today));
    }

    public BigDecimal availableAmount() {
        return totalLimit.subtract(usedAmount);
    }

    public boolean canUse(BigDecimal amount) {
        validatePositiveAmount(amount);
        return availableAmount().compareTo(amount) >= 0;
    }

    public void use(BigDecimal amount) {
        validatePositiveAmount(amount);
        if (availableAmount().compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "사용 가능 한도를 초과했습니다. amount=" + amount
                            + ", availableAmount=" + availableAmount()
            );
        }
        this.usedAmount = this.usedAmount.add(amount);
    }

    private static void validatePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("사용 금액은 필수입니다.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다. amount=" + amount);
        }
    }
}
