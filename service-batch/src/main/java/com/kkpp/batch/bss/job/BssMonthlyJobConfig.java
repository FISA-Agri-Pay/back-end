package com.kkpp.batch.bss.job;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Map;

import com.kkpp.batch.bss.domain.CreditLimit;
import com.kkpp.batch.bss.dto.BssCalculationResult;
import com.kkpp.batch.bss.repository.BssScoreJdbcRepository;
import com.kkpp.batch.bss.service.BssCalculationService;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
public class BssMonthlyJobConfig {

    private static final int CHUNK_SIZE = 100;

    // 매월 ACTIVE 한도를 대상으로 한도 승인 이후 행동 점수인 BSS를 산출하는 Job이다.
    // 실제 실행 주기는 운영 CronJob 또는 스케줄러에서 결정한다.
    @Bean
    public Job bssMonthlyJob(JobRepository jobRepository, Step bssMonthlyStep) {
        return new JobBuilder("bssMonthlyJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(bssMonthlyStep)
                .build();
    }

    @Bean
    public Clock batchClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public Step bssMonthlyStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<CreditLimit> bssMonthlyReader,
            ItemProcessor<CreditLimit, BssCalculationResult> bssMonthlyProcessor,
            ItemWriter<BssCalculationResult> bssMonthlyWriter,
            StepExecutionListener bssMonthlyStepLogger
    ) {
        return new StepBuilder("bssMonthlyStep", jobRepository)
                // BSS는 돈이 직접 움직이는 배치가 아니므로 기존 chunk size 100을 유지한다.
                .<CreditLimit, BssCalculationResult>chunk(CHUNK_SIZE, transactionManager)
                .reader(bssMonthlyReader)
                .processor(bssMonthlyProcessor)
                .writer(bssMonthlyWriter)
                .listener(bssMonthlyStepLogger)
                .build();
    }

    // Reader는 BSS 산출 대상 한도만 결정한다. 이자/원금/연체 원장은 Processor의 서비스에서 한도별로 조회한다.
    @Bean
    @StepScope
    public JpaPagingItemReader<CreditLimit> bssMonthlyReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<CreditLimit>()
                .name("bssMonthlyReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT c
                        FROM CreditLimit c
                        WHERE c.status = :status
                        ORDER BY c.id
                        """)
                .parameterValues(Map.of("status", "ACTIVE"))
                .pageSize(CHUNK_SIZE)
                .build();
    }

    // periodYear/periodMonth JobParameter가 없으면 기본적으로 전월 BSS를 산출한다.
    @Bean
    @StepScope
    public ItemProcessor<CreditLimit, BssCalculationResult> bssMonthlyProcessor(
            BssCalculationService bssCalculationService,
            Clock batchClock,
            @Value("#{jobParameters['periodYear']}") String periodYear,
            @Value("#{jobParameters['periodMonth']}") String periodMonth
    ) {
        YearMonth targetMonth = resolveTargetMonth(periodYear, periodMonth, batchClock);
        LocalDateTime calculatedAt = LocalDateTime.now(batchClock);

        return creditLimit -> bssCalculationService.calculate(creditLimit, targetMonth, calculatedAt);
    }

    // 월별 BSS는 application_id가 없는 사용자 단위 점수이므로 partial unique index에 맞춘 JDBC upsert를 사용한다.
    @Bean
    public ItemWriter<BssCalculationResult> bssMonthlyWriter(BssScoreJdbcRepository bssScoreJdbcRepository) {
        return chunk -> {
            for (BssCalculationResult result : chunk) {
                bssScoreJdbcRepository.upsertMonthly(result);
            }
        };
    }

    @Bean
    public StepExecutionListener bssMonthlyStepLogger() {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                log.info("BSS 월별 산출 Step을 시작합니다. stepName={}", stepExecution.getStepName());
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                log.info(
                        "BSS 월별 산출 Step이 종료되었습니다. stepName={}, status={}, readCount={}, writeCount={}, filterCount={}, skipCount={}",
                        stepExecution.getStepName(),
                        stepExecution.getStatus(),
                        stepExecution.getReadCount(),
                        stepExecution.getWriteCount(),
                        stepExecution.getFilterCount(),
                        stepExecution.getSkipCount()
                );
                return stepExecution.getExitStatus();
            }
        };
    }

    private YearMonth resolveTargetMonth(String periodYear, String periodMonth, Clock batchClock) {
        boolean hasYear = periodYear != null && !periodYear.isBlank();
        boolean hasMonth = periodMonth != null && !periodMonth.isBlank();

        if (hasYear != hasMonth) {
            throw new IllegalArgumentException("BSS 산출 기준 연도와 월은 함께 입력해야 합니다.");
        }

        if (!hasYear) {
            return YearMonth.now(batchClock).minusMonths(1);
        }

        try {
            return YearMonth.of(Integer.parseInt(periodYear), Integer.parseInt(periodMonth));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("BSS 산출 기준 연도와 월은 숫자여야 합니다.", ex);
        }
    }
}
