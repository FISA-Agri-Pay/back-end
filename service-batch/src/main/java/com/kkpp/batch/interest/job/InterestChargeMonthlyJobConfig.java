package com.kkpp.batch.interest.job;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;

import com.kkpp.batch.interest.domain.CreditLimit;
import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.interest.repository.InterestLedgerRepository;
import com.kkpp.batch.interest.service.InterestChargeService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
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

@Configuration
public class InterestChargeMonthlyJobConfig {

    private static final int CHUNK_SIZE = 100;

    // 사용금액이 있는 ACTIVE 한도에 대해 월별 이자 원장을 생성하는 Job이다.
    @Bean
    public Job interestChargeMonthlyJob(JobRepository jobRepository, Step interestChargeMonthlyStep) {
        return new JobBuilder("interestChargeMonthlyJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(interestChargeMonthlyStep)
                .build();
    }

    @Bean
    public Step interestChargeMonthlyStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<CreditLimit> interestChargeMonthlyReader,
            ItemProcessor<CreditLimit, InterestLedger> interestChargeMonthlyProcessor,
            ItemWriter<InterestLedger> interestChargeMonthlyWriter
    ) {
        return new StepBuilder("interestChargeMonthlyStep", jobRepository)
                // 기존 BSS 월별 배치와 동일하게 100건 단위로 처리한다.
                .<CreditLimit, InterestLedger>chunk(CHUNK_SIZE, transactionManager)
                .reader(interestChargeMonthlyReader)
                .processor(interestChargeMonthlyProcessor)
                .writer(interestChargeMonthlyWriter)
                .build();
    }

    // Reader는 배치 대상만 좁힌다. 실제 계산과 중복 확인은 서비스에서 수행한다.
    @Bean
    @StepScope
    public JpaPagingItemReader<CreditLimit> interestChargeMonthlyReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<CreditLimit>()
                .name("interestChargeMonthlyReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT c
                        FROM InterestCreditLimit c
                        WHERE c.status = :status
                          AND c.usedAmount > 0
                        ORDER BY c.id
                        """)
                .parameterValues(Map.of("status", "ACTIVE"))
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<CreditLimit, InterestLedger> interestChargeMonthlyProcessor(
            InterestChargeService interestChargeService,
            Clock batchClock,
            @Value("#{jobParameters['targetYear']}") String targetYear,
            @Value("#{jobParameters['targetMonth']}") String targetMonth
    ) {
        // targetYear/targetMonth가 없으면 실행 시점의 현재 월을 청구 대상 월로 본다.
        YearMonth chargeMonth = resolveTargetMonth(targetYear, targetMonth, batchClock);
        LocalDateTime createdAt = LocalDateTime.now(batchClock);

        // Processor에서 null을 반환하면 Spring Batch가 해당 item을 저장 대상에서 제외한다.
        return creditLimit -> interestChargeService.createMonthlyInterestLedger(creditLimit, chargeMonth, createdAt)
                .orElse(null);
    }

    // Processor에서 생성 대상으로 확정된 이자 원장만 저장한다.
    @Bean
    public ItemWriter<InterestLedger> interestChargeMonthlyWriter(
            InterestLedgerRepository interestLedgerRepository
    ) {
        return chunk -> interestLedgerRepository.saveAll(chunk.getItems());
    }

    // 월 단위 배치이므로 연/월 파라미터는 둘 다 있거나 둘 다 없어야 한다.
    private YearMonth resolveTargetMonth(String targetYear, String targetMonth, Clock batchClock) {
        boolean hasYear = targetYear != null && !targetYear.isBlank();
        boolean hasMonth = targetMonth != null && !targetMonth.isBlank();

        if (hasYear != hasMonth) {
            throw new IllegalArgumentException("이자 원장 생성 배치의 targetYear와 targetMonth JobParameter는 함께 설정되어야 합니다.");
        }

        if (!hasYear) {
            return YearMonth.now(batchClock);
        }

        try {
            return YearMonth.of(Integer.parseInt(targetYear), Integer.parseInt(targetMonth));
        } catch (NumberFormatException | DateTimeException ex) {
            throw new IllegalArgumentException("이자 원장 생성 배치의 targetYear 또는 targetMonth JobParameter 값이 올바르지 않습니다.", ex);
        }
    }
}
