package com.kkpp.core.credit.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "credit_limit_applications", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLimitApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "is_reapplication", nullable = false)
    private Boolean isReapplication;

    @Column(name = "reviewed_by_admin_public_id")
    private UUID reviewedByAdminPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public static CreditLimitApplication create(UUID userPublicId) {
        Objects.requireNonNull(userPublicId, "userPublicId must not be null");

        CreditLimitApplication application = new CreditLimitApplication();
        application.publicId = UUID.randomUUID();
        application.userPublicId = userPublicId;
        application.requestedAmount = BigDecimal.ONE;
        application.isReapplication = Boolean.FALSE;
        application.status = ApplicationStatus.PENDING;
        application.appliedAt = LocalDateTime.now();
        return application;
    }
}
