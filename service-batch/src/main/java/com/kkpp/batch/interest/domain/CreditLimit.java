package com.kkpp.batch.interest.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "InterestCreditLimit")
@Table(name = "credit_limits", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String cropTypeSnapshot;

    @Column(nullable = false)
    private BigDecimal usedAmount;

    @Column(nullable = false)
    private BigDecimal interestRate;

    @Column(nullable = false)
    private Integer interestDueDay;

    @Column(nullable = false)
    private LocalDate principalDueDate;

    @Column(nullable = false)
    private String status;

    public static int calculateDefaultInterestDueDay(LocalDate approvedDate) {
        return Math.min(approvedDate.getDayOfMonth() + 10, 28);
    }
}
