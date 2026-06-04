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

@Entity(name = "WalletInterestLedger")
@Table(name = "interest_ledger", schema = "core")
// 조회 전용 매핑입니다. 이 API는 배치가 만든 이자 원장을 읽기만 하고 새 원장을 생성하지 않습니다.
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestLedger extends BaseEntity {

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

    @Column(name = "base_principal", nullable = false)
    private BigDecimal basePrincipal;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "interest_amount", nullable = false)
    private BigDecimal interestAmount;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(nullable = false, length = 20)
    private String status;

    public BigDecimal getUnpaidAmount() {
        BigDecimal totalAmount = interestAmount == null ? BigDecimal.ZERO : interestAmount;
        BigDecimal paidAmount = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        BigDecimal unpaidAmount = totalAmount.subtract(paidAmount);
        return unpaidAmount.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : unpaidAmount;
    }
}
