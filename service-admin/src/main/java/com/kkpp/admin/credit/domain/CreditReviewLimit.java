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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "credit_limits", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 관리자 승인 후 생성되는 실제 사용자 한도를 나타내는 credit_limits 테이블 매핑 엔티티
// 결제 시스템은 이 테이블의 ACTIVE 한도를 기준으로 외상 결제 가능 여부를 판단한다.
public class CreditReviewLimit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private CreditReviewUser user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CreditReviewApplication application;

    @Column(name = "total_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalLimit;

    @Column(name = "used_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal usedAmount;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "principal_due_date", nullable = false)
    private LocalDate principalDueDate;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditLimitStatus status;

    // 승인된 신청을 바탕으로 실제 사용 가능한 ACTIVE 한도 엔티티를 만든다.
    // usedAmount는 신규 발급 시점에는 아직 사용 전이므로 0으로 시작한다.
    public static CreditReviewLimit issue(
            CreditReviewApplication application,
            BigDecimal totalLimit,
            BigDecimal interestRate,
            LocalDate principalDueDate,
            LocalDate expiresAt
    ) {
        CreditReviewLimit limit = new CreditReviewLimit();
        limit.publicId = UUID.randomUUID();
        limit.user = application.getUser();
        limit.application = application;
        limit.totalLimit = totalLimit;
        limit.usedAmount = BigDecimal.ZERO;
        limit.interestRate = interestRate;
        limit.principalDueDate = principalDueDate;
        limit.expiresAt = expiresAt;
        limit.status = CreditLimitStatus.ACTIVE;
        return limit;
    }
}
