package com.kkpp.batch.bss.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import com.kkpp.batch.bss.domain.PeriodType;
import com.kkpp.batch.bss.dto.BssCalculationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class BssScoreJdbcRepositoryTest {

    @Test
    void upsertMonthlyStoresMonthlyScoreWithNullApplicationIdConflictPolicy() {
        NamedParameterJdbcTemplate jdbcTemplate = Mockito.mock(NamedParameterJdbcTemplate.class);
        BssScoreJdbcRepository repository = new BssScoreJdbcRepository(jdbcTemplate);
        BssCalculationResult result = new BssCalculationResult(
                1L,
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
        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("application_id");
        assertThat(sql).contains("NULL");
        assertThat(sql).contains("ON CONFLICT (user_id, period_type, period_year, period_month)");
        assertThat(sql).contains("WHERE application_id IS NULL");
        assertThat(sql).contains("DO UPDATE SET");

        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("userId")).isEqualTo(1L);
        assertThat(params.getValue("periodType")).isEqualTo("MONTHLY");
        assertThat(params.getValue("periodYear")).isEqualTo(2026);
        assertThat(params.getValue("periodMonth")).isEqualTo(5);
        assertThat(params.getValue("monthlyScore")).isEqualTo(84);
        assertThat(params.getValue("totalScore")).isEqualTo(84);
        assertThat(params.getValue("calculatedAt")).isEqualTo(LocalDateTime.of(2026, 6, 1, 1, 0));
    }
}
