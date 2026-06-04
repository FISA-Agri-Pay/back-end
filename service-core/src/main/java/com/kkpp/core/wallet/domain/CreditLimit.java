package com.kkpp.core.wallet.domain;

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
import org.hibernate.annotations.Immutable;

@Entity(name = "WalletCreditLimit")
@Table(name = "credit_limits", schema = "core")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLimit extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

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

    @Column(name = "total_limit", nullable = false)
    private BigDecimal totalLimit;

    @Column(name = "used_amount", nullable = false)
    private BigDecimal usedAmount;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "interest_due_day", nullable = false)
    private Integer interestDueDay;

    @Column(name = "principal_due_date", nullable = false)
    private LocalDate principalDueDate;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(nullable = false, length = 20)
    private String status;
}
