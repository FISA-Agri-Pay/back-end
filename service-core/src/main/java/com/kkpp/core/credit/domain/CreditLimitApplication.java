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

    @Column(nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "reviewed_by_admin_public_id")
    private UUID reviewedByAdminPublicId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(nullable = false)
    private Boolean isReapplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @Column
    private LocalDateTime decidedAt;

    @Column
    private String rejectionReason;

    public static CreditLimitApplication create(UUID userPublicId, BigDecimal requestedAmount) {
        Objects.requireNonNull(userPublicId, "userPublicId는 필수입니다.");
        Objects.requireNonNull(requestedAmount, "requestedAmount는 필수입니다.");

        CreditLimitApplication application = new CreditLimitApplication();
        application.publicId = UUID.randomUUID();
        application.userPublicId = userPublicId;
        application.requestedAmount = requestedAmount;
        application.isReapplication = false;
        application.status = ApplicationStatus.PENDING;
        application.appliedAt = LocalDateTime.now();
        return application;
    }
}
