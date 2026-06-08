package com.kkpp.admin.credit.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "credit_limit_applications", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 한도 심사 신청의 중심 엔티티
// 관리자는 이 엔티티의 상태를 PENDING에서 APPROVED 또는 REJECTED로 변경한다.
public class CreditReviewApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_public_id", referencedColumnName = "public_id", nullable = false)
    private CreditReviewUser user;

    @Column(name = "reviewed_by_admin_public_id")
    private UUID reviewedByAdminPublicId;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "is_reapplication", nullable = false)
    private Boolean reapplication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditReviewStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    // 신청을 승인 상태로 변경한다.
    // 승인 금액은 DB 체크 제약상 APPROVED 상태에서 반드시 필요하므로 양수인지 검증한다.
    public void approve(UUID reviewedByAdminPublicId, BigDecimal approvedAmount, LocalDateTime decidedAt) {
        if (status != CreditReviewStatus.PENDING) {
            throw new IllegalStateException("Only PENDING applications can be approved.");
        }
        if (reviewedByAdminPublicId == null) {
            throw new IllegalArgumentException("Reviewer id is required.");
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException("Decision time is required.");
        }
        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Approved amount must be positive.");
        }
        this.reviewedByAdminPublicId = reviewedByAdminPublicId;
        this.approvedAmount = approvedAmount;
        this.rejectionReason = null;
        this.status = CreditReviewStatus.APPROVED;
        this.decidedAt = decidedAt;
    }

    // 신청을 반려 상태로 변경한다.
    // 반려 사유는 관리자 화면에서 반려 이력을 확인할 수 있도록 필수값으로 검증한다.
    public void reject(UUID reviewedByAdminPublicId, String rejectionReason, LocalDateTime decidedAt) {
        if (status != CreditReviewStatus.PENDING) {
            throw new IllegalStateException("Only PENDING applications can be rejected.");
        }
        if (reviewedByAdminPublicId == null) {
            throw new IllegalArgumentException("Reviewer id is required.");
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException("Decision time is required.");
        }
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must not be blank.");
        }
        this.reviewedByAdminPublicId = reviewedByAdminPublicId;
        this.approvedAmount = null;
        this.rejectionReason = rejectionReason;
        this.status = CreditReviewStatus.REJECTED;
        this.decidedAt = decidedAt;
    }
}
