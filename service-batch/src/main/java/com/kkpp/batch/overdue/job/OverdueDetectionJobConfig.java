package com.kkpp.batch.overdue.job;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.overdue.service.OverdueDetectionService;
import com.kkpp.batch.principal.repayment.domain.PrincipalRepaymentLedger;
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
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
public class OverdueDetectionJobConfig {

    private static final int CHUNK_SIZE = 100;
    private static final List<String> INTEREST_DETECTION_STATUSES = List.of(
            InterestLedger.STATUS_UPCOMING,
            InterestLedger.STATUS_PARTIAL,
            InterestLedger.STATUS_OVERDUE
    );
    private static final List<String> PRINCIPAL_DETECTION_STATUSES = List.of(
            PrincipalRepaymentLedger.STATUS_UPCOMING,
            PrincipalRepaymentLedger.STATUS_PARTIAL,
            PrincipalRepaymentLedger.STATUS_OVERDUE
    );

    @Bean
    public Job overdueDetectionJob(
            JobRepository jobRepository,
            Step interestOverdueDetectionStep,
            Step principalOverdueDetectionStep
    ) {
        return new JobBuilder("overdueDetectionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(interestOverdueDetectionStep)
                .next(principalOverdueDetectionStep)
                .build();
    }

    @Bean
    public Step interestOverdueDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaCursorItemReader<InterestLedger> interestOverdueDetectionReader,
            ItemProcessor<InterestLedger, InterestLedger> interestOverdueDetectionProcessor,
            ItemWriter<InterestLedger> overdueDetectionNoOpWriter,
            @Qualifier("interestOverdueDetectionStepLogger") StepExecutionListener interestOverdueDetectionStepLogger
    ) {
        return new StepBuilder("interestOverdueDetectionStep", jobRepository)
                .<InterestLedger, InterestLedger>chunk(CHUNK_SIZE, transactionManager)
                .reader(interestOverdueDetectionReader)
                .processor(interestOverdueDetectionProcessor)
                .writer(overdueDetectionNoOpWriter)
                .listener(interestOverdueDetectionStepLogger)
                .build();
    }

    @Bean
    public Step principalOverdueDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaCursorItemReader<PrincipalRepaymentLedger> principalOverdueDetectionReader,
            ItemProcessor<PrincipalRepaymentLedger, PrincipalRepaymentLedger> principalOverdueDetectionProcessor,
            ItemWriter<PrincipalRepaymentLedger> principalOverdueDetectionNoOpWriter,
            @Qualifier("principalOverdueDetectionStepLogger") StepExecutionListener principalOverdueDetectionStepLogger
    ) {
        return new StepBuilder("principalOverdueDetectionStep", jobRepository)
                .<PrincipalRepaymentLedger, PrincipalRepaymentLedger>chunk(CHUNK_SIZE, transactionManager)
                .reader(principalOverdueDetectionReader)
                .processor(principalOverdueDetectionProcessor)
                .writer(principalOverdueDetectionNoOpWriter)
                .listener(principalOverdueDetectionStepLogger)
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<InterestLedger> interestOverdueDetectionReader(
            EntityManagerFactory entityManagerFactory,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);

        // 납부일 당일은 연체가 아니므로 dueDate < targetDate인 원장만 읽는다.
        // Cursor Reader를 사용해 처리 중 status가 OVERDUE로 바뀌어도 offset paging 누락이 생기지 않게 한다.
        return new JpaCursorItemReaderBuilder<InterestLedger>()
                .name("interestOverdueDetectionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT i
                        FROM InterestChargeLedger i
                        WHERE i.dueDate < :today
                          AND i.status IN :statuses
                          AND i.amountPaid < i.interestAmount
                        ORDER BY i.dueDate, i.id
                        """)
                .parameterValues(Map.of(
                        "today", today,
                        "statuses", INTEREST_DETECTION_STATUSES
                ))
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<PrincipalRepaymentLedger> principalOverdueDetectionReader(
            EntityManagerFactory entityManagerFactory,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);

        // 원금 원장은 amount_due가 아니라 principalAmount를 기준으로 미상환 여부를 판단한다.
        return new JpaCursorItemReaderBuilder<PrincipalRepaymentLedger>()
                .name("principalOverdueDetectionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT p
                        FROM PrincipalAutoPaymentLedger p
                        WHERE p.dueDate < :today
                          AND p.status IN :statuses
                          AND p.amountPaid < p.principalAmount
                        ORDER BY p.dueDate, p.id
                        """)
                .parameterValues(Map.of(
                        "today", today,
                        "statuses", PRINCIPAL_DETECTION_STATUSES
                ))
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<InterestLedger, InterestLedger> interestOverdueDetectionProcessor(
            OverdueDetectionService overdueDetectionService,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);
        LocalDateTime now = LocalDateTime.now(batchClock);

        // 실제 처리 전 서비스에서 잠금 조회와 최신 상태 재검증을 한 번 더 수행한다.
        return interestLedger -> overdueDetectionService.detectInterestOverdue(interestLedger.getId(), today, now)
                .orElse(null);
    }

    @Bean
    @StepScope
    public ItemProcessor<PrincipalRepaymentLedger, PrincipalRepaymentLedger> principalOverdueDetectionProcessor(
            OverdueDetectionService overdueDetectionService,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);
        LocalDateTime now = LocalDateTime.now(batchClock);

        return principalLedger -> overdueDetectionService.detectPrincipalOverdue(principalLedger.getId(), today, now)
                .orElse(null);
    }

    @Bean
    public ItemWriter<InterestLedger> overdueDetectionNoOpWriter() {
        // 상태 변경과 연체 이력 저장은 Processor가 호출한 서비스 트랜잭션 안에서 끝난다.
        return chunk -> {
        };
    }

    @Bean
    public ItemWriter<PrincipalRepaymentLedger> principalOverdueDetectionNoOpWriter() {
        return chunk -> {
        };
    }

    @Bean
    public StepExecutionListener interestOverdueDetectionStepLogger() {
        return stepLogger("이자 연체 감지");
    }

    @Bean
    public StepExecutionListener principalOverdueDetectionStepLogger() {
        return stepLogger("원금 연체 감지");
    }

    private StepExecutionListener stepLogger(String label) {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                log.info("{} Step을 시작합니다. stepName={}", label, stepExecution.getStepName());
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                log.info(
                        "{} Step이 종료되었습니다. stepName={}, status={}, readCount={}, writeCount={}, filterCount={}, skipCount={}",
                        label,
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

    private LocalDate resolveTargetDate(String targetDate, Clock batchClock) {
        if (targetDate == null || targetDate.isBlank()) {
            return LocalDate.now(batchClock);
        }

        try {
            return LocalDate.parse(targetDate);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("연체 감지 배치 targetDate JobParameter 값이 올바르지 않습니다.", ex);
        }
    }
}
