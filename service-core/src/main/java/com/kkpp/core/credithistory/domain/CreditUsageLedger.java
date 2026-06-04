package com.kkpp.core.credithistory.domain;

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

@Entity(name = "CreditHistoryUsageLedger")
@Table(name = "credit_usage_ledger", schema = "core")
// 조회 전용 매핑입니다. 외상 사용 원장 생성은 결제 확정 처리 흐름에서만 수행합니다.
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditUsageLedger {

    public static final String TYPE_PURCHASE = "PURCHASE";
    public static final String TYPE_CANCEL = "CANCEL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "credit_limit_public_id", nullable = false)
    private UUID creditLimitPublicId;

    @Column(name = "payment_request_public_id")
    private UUID paymentRequestPublicId;

    @Column(name = "order_public_id")
    private UUID orderPublicId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "usage_type", nullable = false, length = 20)
    private String usageType;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
