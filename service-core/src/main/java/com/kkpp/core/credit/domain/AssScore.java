package com.kkpp.core.credit.domain;

import com.kkpp.common.core.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ass_scores", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssScore extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_public_id", referencedColumnName = "public_id", nullable = false)
    private CreditLimitApplication application;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "estimated_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedIncome;

    @Column(name = "price_snapshot_date", nullable = false)
    private LocalDate priceSnapshotDate;

    @Column(name = "income_score", nullable = false)
    private int incomeScore;

    @Column(name = "insurance_score", nullable = false)
    private int insuranceScore;

    @Column(name = "farming_career_score", nullable = false)
    private int farmingCareerScore;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    public static AssScore create(CreditLimitApplication application, BigDecimal estimatedIncome,
                                  LocalDate priceSnapshotDate, int incomeScore, int insuranceScore,
                                  int farmingCareerScore, int totalScore, LocalDateTime calculatedAt) {
        AssScore score = new AssScore();
        score.application = application;
        score.userPublicId = application.getUserPublicId();
        score.estimatedIncome = estimatedIncome;
        score.priceSnapshotDate = priceSnapshotDate;
        score.incomeScore = incomeScore;
        score.insuranceScore = insuranceScore;
        score.farmingCareerScore = farmingCareerScore;
        score.totalScore = totalScore;
        score.calculatedAt = calculatedAt;
        return score;
    }
}
