package com.kkpp.batch.bss.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.kkpp.common.core.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "loan_overdue_ledger", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanOverdueLedger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID publicId;

    @Column(nullable = false)
    private UUID userPublicId;

    @Column(nullable = false)
    private UUID creditLimitPublicId;

    private UUID interestLedgerPublicId;

    private UUID principalRepaymentPublicId;

    @Column(nullable = false)
    private String overdueType;

    @Column(nullable = false)
    private BigDecimal overdueAmount;

    @Column(nullable = false)
    private Integer overdueDays;

    private LocalDateTime resolvedAt;

}
