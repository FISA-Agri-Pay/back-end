package com.kkpp.batch.interest.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

@Entity(name = "InterestPaymentWalletTransaction")
@Table(name = "wallet_transactions", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction extends BaseTimeEntity {

    public static final String TYPE_INTEREST_PAYMENT = "INTEREST_PAYMENT";
    public static final String TYPE_PRINCIPAL_PAYMENT = "PRINCIPAL_PAYMENT";
    private static final String RELATED_TYPE_INTEREST_LEDGER = "INTEREST_LEDGER";
    private static final String RELATED_TYPE_PRINCIPAL_REPAYMENT_LEDGER = "PRINCIPAL_REPAYMENT_LEDGER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private String transactionType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal balanceAfter;

    private String relatedType;

    private Long relatedId;

    private String description;

    @Column(nullable = false)
    private LocalDateTime transactedAt;

    public static WalletTransaction interestPayment(
            Long walletId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Long interestLedgerId,
            LocalDateTime now
    ) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.walletId = walletId;
        transaction.transactionType = TYPE_INTEREST_PAYMENT;
        transaction.amount = amount;
        transaction.balanceAfter = balanceAfter;
        transaction.relatedType = RELATED_TYPE_INTEREST_LEDGER;
        transaction.relatedId = interestLedgerId;
        transaction.description = "이자 자동 상환";
        transaction.transactedAt = now;
        return transaction;
    }

    public static WalletTransaction principalPayment(
            Long walletId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Long principalRepaymentLedgerId,
            LocalDateTime now
    ) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.walletId = walletId;
        transaction.transactionType = TYPE_PRINCIPAL_PAYMENT;
        transaction.amount = amount;
        transaction.balanceAfter = balanceAfter;
        transaction.relatedType = RELATED_TYPE_PRINCIPAL_REPAYMENT_LEDGER;
        transaction.relatedId = principalRepaymentLedgerId;
        transaction.description = "원금 자동 상환";
        transaction.transactedAt = now;
        return transaction;
    }
}
