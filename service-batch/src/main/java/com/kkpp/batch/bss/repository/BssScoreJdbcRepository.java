package com.kkpp.batch.bss.repository;

import com.kkpp.batch.bss.dto.BssCalculationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BssScoreJdbcRepository {

    // bss_scores는 application_id가 NULL인 월별 사용자 점수에 partial unique index를 사용한다.
    // 그래서 ON CONFLICT ON CONSTRAINT가 아니라 컬럼 목록과 WHERE 조건을 함께 지정한다.
    private static final String UPSERT_MONTHLY_BSS_SQL = """
            INSERT INTO core.bss_scores (
                user_id,
                application_id,
                period_type,
                period_year,
                period_month,
                monthly_score,
                total_score,
                calculated_at,
                created_at
            )
            VALUES (
                :userId,
                NULL,
                :periodType,
                :periodYear,
                :periodMonth,
                :monthlyScore,
                :totalScore,
                :calculatedAt,
                now()
            )
            ON CONFLICT (user_id, period_type, period_year, period_month)
            WHERE application_id IS NULL
            DO UPDATE SET
                monthly_score = EXCLUDED.monthly_score,
                total_score = EXCLUDED.total_score,
                calculated_at = EXCLUDED.calculated_at
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // 같은 사용자와 같은 월의 BSS가 이미 있으면 중복 insert가 아니라 최신 계산값으로 update한다.
    public void upsertMonthly(BssCalculationResult result) {
        namedParameterJdbcTemplate.update(
                UPSERT_MONTHLY_BSS_SQL,
                new MapSqlParameterSource()
                        .addValue("userId", result.userId())
                        .addValue("periodType", result.periodType().name())
                        .addValue("periodYear", result.periodYear())
                        .addValue("periodMonth", result.periodMonth())
                        .addValue("monthlyScore", result.monthlyScore())
                        .addValue("totalScore", result.totalScore())
                        .addValue("calculatedAt", result.calculatedAt())
        );
    }
}
