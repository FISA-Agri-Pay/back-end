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

@Entity
@Table(name = "ass_scores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssScore extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private CreditLimitApplication application;

    @Column(nullable = false)
    private int fieldAreaScore;

    @Column(nullable = false)
    private int cropScore;

    @Column(nullable = false)
    private int insuranceScore;

    @Column(nullable = false)
    private int farmingCareerScore;

    @Column(nullable = false)
    private int totalScore;

    public static AssScore create(CreditLimitApplication application, int fieldAreaScore, int cropScore,
                                  int insuranceScore, int farmingCareerScore) {
        AssScore score = new AssScore();
        score.application = application;
        score.fieldAreaScore = fieldAreaScore;
        score.cropScore = cropScore;
        score.insuranceScore = insuranceScore;
        score.farmingCareerScore = farmingCareerScore;
        score.totalScore = fieldAreaScore + cropScore + insuranceScore + farmingCareerScore;
        return score;
    }
}
