package com.kkpp.batch.bss.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
@Table(name = "principal_repayment_ledger", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrincipalRepaymentLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID publicId;

    @Column(nullable = false)
    private UUID creditLimitPublicId;

    @Column(nullable = false)
    private UUID orderPublicId;

    @Column(nullable = false)
    private UUID paymentRequestPublicId;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private BigDecimal principalAmount;

    @Column(nullable = false)
    private BigDecimal amountPaid;
}
