package com.kkpp.admin.bnpl.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@Table(name = "principal_repayment_ledger", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 구매 건별 원금 상환 이력을 관리하는 principal_repayment_ledger 테이블 매핑 엔티티
// 당월 회수 예정액 KPI와 연체 현황 dueDate 조합에 사용된다.
public class PrincipalRepaymentLedger extends BaseEntity {

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

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "principal_amount", precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "amount_paid", precision = 15, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerStatus status;
}
