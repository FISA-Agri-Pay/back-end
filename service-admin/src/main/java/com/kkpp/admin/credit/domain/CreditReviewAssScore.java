package com.kkpp.admin.credit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ass_scores", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 한도 신청 시점에 산정된 ASS 점수를 조회하기 위한 ass_scores 테이블 매핑 엔티티
// 관리자 화면의 시스템 추출 데이터와 점수 표시 근거가 된다.
public class CreditReviewAssScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CreditReviewApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private CreditReviewUser user;

    @Column(name = "estimated_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedIncome;

    @Column(name = "price_snapshot_date", nullable = false)
    private LocalDate priceSnapshotDate;

    @Column(name = "income_score", nullable = false)
    private Integer incomeScore;

    @Column(name = "insurance_score", nullable = false)
    private Integer insuranceScore;

    @Column(name = "farming_career_score", nullable = false)
    private Integer farmingCareerScore;

    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
