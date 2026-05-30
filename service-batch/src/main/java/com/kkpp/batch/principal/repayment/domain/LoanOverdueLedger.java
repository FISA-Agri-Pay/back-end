package com.kkpp.batch.principal.repayment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

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

@Entity(name = "PrincipalRepaymentLoanOverdueLedger")
@Table(name = "loan_overdue_ledger", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanOverdueLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID publicId;

    @Column(nullable = false)
    private UUID creditLimitPublicId;

    private UUID principalRepaymentPublicId;

    @Column(nullable = false)
    private BigDecimal overdueAmount;

    private LocalDateTime resolvedAt;

    // 연체 이력은 삭제하지 않고 해소 시각과 해소 금액만 남긴다.
    public void resolve(LocalDateTime resolvedAt) {
        if (resolvedAt == null) {
            throw new IllegalArgumentException("연체 해소 시각은 null일 수 없습니다.");
        }

        this.resolvedAt = resolvedAt;
    }
}
