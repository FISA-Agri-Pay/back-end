package com.kkpp.batch.bss.job;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

import com.kkpp.batch.bss.domain.CreditLimit;
import com.kkpp.batch.bss.dto.BssCalculationResult;
import com.kkpp.batch.bss.repository.BssScoreJdbcRepository;
import com.kkpp.batch.bss.service.BssCalculationService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
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

@Configuration
public class BssMonthlyJobConfig {

    private static final int CHUNK_SIZE = 100;

    // 매월 실행되는 BSS 산출 Job이다. K8s CronJob에서 이 Job을 실행하는 것을 전제로 한다.
    @Bean
    public Job bssMonthlyJob(JobRepository jobRepository, Step bssMonthlyStep) {
        return new JobBuilder("bssMonthlyJob", jobRepository)
                .start(bssMonthlyStep)
                .build();
    }

    @Bean
    public Step bssMonthlyStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<CreditLimit> bssMonthlyReader,
            ItemProcessor<CreditLimit, BssCalculationResult> bssMonthlyProcessor,
            ItemWriter<BssCalculationResult> bssMonthlyWriter
    ) {
        return new StepBuilder("bssMonthlyStep", jobRepository)
                // ACTIVE 한도를 100건씩 읽고, 각 한도별 BSS 계산 결과를 bss_scores에 저장한다.
                .<CreditLimit, BssCalculationResult>chunk(CHUNK_SIZE, transactionManager)
                .reader(bssMonthlyReader)
                .processor(bssMonthlyProcessor)
                .writer(bssMonthlyWriter)
                .build();
    }

    // Reader는 산출 대상만 결정한다. 이자/원금/연체 조회는 Processor에서 한도별로 수행한다.
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
            @Value("#{jobParameters['periodYear']}") String periodYear,
            @Value("#{jobParameters['periodMonth']}") String periodMonth
    ) {
        YearMonth targetMonth = resolveTargetMonth(periodYear, periodMonth);
        LocalDateTime calculatedAt = LocalDateTime.now();

        return creditLimit -> bssCalculationService.calculate(creditLimit, targetMonth, calculatedAt);
    }

    // partial unique index를 사용하므로 JPA save가 아니라 PostgreSQL upsert 전용 Repository를 사용한다.
    @Bean
    public ItemWriter<BssCalculationResult> bssMonthlyWriter(BssScoreJdbcRepository bssScoreJdbcRepository) {
        return chunk -> {
            for (BssCalculationResult result : chunk) {
                bssScoreJdbcRepository.upsertMonthly(result);
            }
        };
    }

    private YearMonth resolveTargetMonth(String periodYear, String periodMonth) {
        if (periodYear != null && periodMonth != null) {
            return YearMonth.of(Integer.parseInt(periodYear), Integer.parseInt(periodMonth));
        }

        return YearMonth.now().minusMonths(1);
    }
}
