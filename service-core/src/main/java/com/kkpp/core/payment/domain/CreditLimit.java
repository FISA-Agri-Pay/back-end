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
import java.util.UUID;
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

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "application_public_id", nullable = false, unique = true)
    private UUID applicationPublicId;

    @Column(name = "crop_type_snapshot", nullable = false, length = 30)
    private String cropTypeSnapshot;

    @Column(name = "total_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalLimit;

    @Column(name = "used_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal usedAmount;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "interest_due_day", nullable = false)
    private Integer interestDueDay;

    @Column(name = "principal_due_date", nullable = false)
    private LocalDate principalDueDate;

    @Column(name = "expires_at")
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
