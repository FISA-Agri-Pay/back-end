package com.kkpp.batch.bss.repository;

import com.kkpp.batch.bss.dto.BssCalculationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BssScoreJdbcRepository {

    private static final String UPDATE_MONTHLY_BSS_SQL = """
            UPDATE core.bss_scores
            SET monthly_score = :monthlyScore,
                total_score = :totalScore,
                calculated_at = :calculatedAt
            WHERE user_public_id = :userPublicId
              AND application_public_id IS NULL
              AND period_type = :periodType
              AND period_year = :periodYear
              AND period_month = :periodMonth
            """;

    private static final String INSERT_MONTHLY_BSS_SQL = """
            INSERT INTO core.bss_scores (
                user_public_id,
                application_public_id,
                period_type,
                period_year,
                period_month,
                monthly_score,
                total_score,
                calculated_at,
                created_at
            )
            VALUES (
                :userPublicId,
                NULL,
                :periodType,
                :periodYear,
                :periodMonth,
                :monthlyScore,
                :totalScore,
                :calculatedAt,
                now()
            )
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public void upsertMonthly(BssCalculationResult result) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userPublicId", result.userPublicId())
                .addValue("periodType", result.periodType().name())
                .addValue("periodYear", result.periodYear())
                .addValue("periodMonth", result.periodMonth())
                .addValue("monthlyScore", result.monthlyScore())
                .addValue("totalScore", result.totalScore())
                .addValue("calculatedAt", result.calculatedAt());

        int updatedCount = namedParameterJdbcTemplate.update(UPDATE_MONTHLY_BSS_SQL, parameters);
        if (updatedCount == 0) {
            namedParameterJdbcTemplate.update(INSERT_MONTHLY_BSS_SQL, parameters);
        }
    }
}
