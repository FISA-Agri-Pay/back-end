package com.kkpp.core.wallet.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity(name = "WalletPrincipalRepaymentLedger")
@Table(name = "principal_repayment_ledger", schema = "core")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrincipalRepaymentLedger extends BaseEntity {

    public static final String STATUS_UPCOMING = "UPCOMING";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_OVERDUE = "OVERDUE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "credit_limit_public_id", nullable = false)
    private UUID creditLimitPublicId;

    @Column(name = "order_public_id", nullable = false, unique = true)
    private UUID orderPublicId;

    @Column(name = "payment_request_public_id")
    private UUID paymentRequestPublicId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_amount", nullable = false)
    private BigDecimal principalAmount;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(nullable = false, length = 20)
    private String status;
}
