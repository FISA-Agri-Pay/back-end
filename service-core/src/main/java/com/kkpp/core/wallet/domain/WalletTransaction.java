package com.kkpp.core.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "wallet_transactions", schema = "core")
// 조회 전용 매핑입니다. 거래 생성은 입금/납부 처리 로직에서 담당합니다.
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction {

    public static final String TYPE_DEPOSIT = "DEPOSIT";
    public static final String TYPE_INTEREST_PAYMENT = "INTEREST_PAYMENT";
    public static final String TYPE_PRINCIPAL_PAYMENT = "PRINCIPAL_PAYMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "wallet_public_id", nullable = false)
    private UUID walletPublicId;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "related_type", length = 50)
    private String relatedType;

    @Column(name = "related_public_id")
    private UUID relatedPublicId;

    @Column
    private String description;

    @Column(name = "transacted_at", nullable = false)
    private LocalDateTime transactedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
