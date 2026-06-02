package com.kkpp.payment.domain;

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

@Getter
@Entity
@Table(name = "interest_ledger", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "credit_limit_public_id", nullable = false)
    private UUID creditLimitPublicId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrincipal;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal interestAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid;

    private LocalDateTime paidAt;

    @Column(nullable = false, length = 20)
    private String status;

    public static InterestLedger upcoming(
            UUID creditLimitPublicId,
            BigDecimal basePrincipal,
            LocalDate dueDate,
            BigDecimal interestAmount
    ) {
        validateRequiredId(creditLimitPublicId, "creditLimitPublicId");
        validatePositiveAmount(basePrincipal, "?댁옄 湲곗? ?먭툑");
        validateNonNegativeAmount(interestAmount, "?댁옄 湲덉븸");
        validateRequiredDueDate(dueDate);

        InterestLedger ledger = new InterestLedger();
        ledger.publicId = UUID.randomUUID();
        ledger.creditLimitPublicId = creditLimitPublicId;
        ledger.basePrincipal = basePrincipal;
        ledger.dueDate = dueDate;
        ledger.interestAmount = interestAmount;
        ledger.amountPaid = BigDecimal.ZERO;
        ledger.status = "UPCOMING";
        return ledger;
    }

    private static void validateRequiredId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + "???꾩닔?낅땲??");
        }
    }

    private static void validatePositiveAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + "? ?꾩닔?낅땲??");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + "? 0蹂대떎 而ㅼ빞 ?⑸땲?? amount=" + amount);
        }
    }

    private static void validateNonNegativeAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + "? ?꾩닔?낅땲??");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + "? ?뚯닔?????놁뒿?덈떎. amount=" + amount);
        }
    }

    private static void validateRequiredDueDate(LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("?댁옄 ?⑸? ?덉젙?쇱? ?꾩닔?낅땲??");
        }
    }
}

