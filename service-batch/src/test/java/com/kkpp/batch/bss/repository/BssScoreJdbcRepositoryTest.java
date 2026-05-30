package com.kkpp.batch.bss.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import com.kkpp.batch.bss.domain.PeriodType;
import com.kkpp.batch.bss.dto.BssCalculationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class BssScoreJdbcRepositoryTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111148");

    @Test
    void upsertMonthlyUpdatesThenInsertsWhenExistingMonthlyScoreIsMissing() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(0);
        BssScoreJdbcRepository repository = new BssScoreJdbcRepository(jdbcTemplate);
        BssCalculationResult result = new BssCalculationResult(
                USER_PUBLIC_ID,
                24,
                40,
                20,
                84,
                84,
                PeriodType.MONTHLY,
                2026,
                5,
                LocalDateTime.of(2026, 6, 1, 1, 0)
        );

        repository.upsertMonthly(result);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(sqlCaptor.capture(), paramsCaptor.capture());

        String updateSql = sqlCaptor.getAllValues().get(0);
        assertThat(updateSql).contains("UPDATE core.bss_scores");
        assertThat(updateSql).contains("user_public_id");
        assertThat(updateSql).contains("application_public_id IS NULL");

        String insertSql = sqlCaptor.getAllValues().get(1);
        assertThat(insertSql).contains("INSERT INTO core.bss_scores");
        assertThat(insertSql).contains("user_public_id");
        assertThat(insertSql).contains("application_public_id");
        assertThat(insertSql).contains("NULL");

        MapSqlParameterSource params = paramsCaptor.getAllValues().get(0);
        assertThat(params.getValue("userPublicId")).isEqualTo(USER_PUBLIC_ID);
        assertThat(params.getValue("periodType")).isEqualTo("MONTHLY");
        assertThat(params.getValue("periodYear")).isEqualTo(2026);
        assertThat(params.getValue("periodMonth")).isEqualTo(5);
        assertThat(params.getValue("monthlyScore")).isEqualTo(84);
        assertThat(params.getValue("totalScore")).isEqualTo(84);
        assertThat(params.getValue("calculatedAt")).isEqualTo(LocalDateTime.of(2026, 6, 1, 1, 0));
    }

    @Test
    void upsertMonthlyDoesNotInsertWhenExistingMonthlyScoreIsUpdated() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        BssScoreJdbcRepository repository = new BssScoreJdbcRepository(jdbcTemplate);
        BssCalculationResult result = new BssCalculationResult(
                USER_PUBLIC_ID,
                24,
                40,
                20,
                84,
                84,
                PeriodType.MONTHLY,
                2026,
                5,
                LocalDateTime.of(2026, 6, 1, 1, 0)
        );

        repository.upsertMonthly(result);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("UPDATE core.bss_scores");
        assertThat(sql).contains("user_public_id");
        assertThat(sql).contains("NULL");
    }
}
