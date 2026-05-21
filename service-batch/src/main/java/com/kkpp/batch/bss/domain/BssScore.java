package com.kkpp.batch.bss.domain;

import java.time.LocalDateTime;

import com.kkpp.common.core.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bss_scores", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BssScore extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    // 이번 배치에서는 월별 사용자 단위 점수만 저장하므로 applicationId는 null로 둔다.
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodType periodType;

    @Column(nullable = false)
    private Integer periodYear;

    private Integer periodMonth;

    private Integer monthlyScore;

    private Integer annualScore;

    @Column(nullable = false)
    private Integer totalScore;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;
}
