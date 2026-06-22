package com.kkpp.batch.principal.repayment.job;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.kkpp.batch.principal.repayment.domain.PrincipalRepaymentLedger;
import com.kkpp.batch.principal.repayment.service.PrincipalAutoPaymentService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
public class PrincipalAutoPaymentJobConfig {

    private static final int CHUNK_SIZE = 1;
    private static final List<String> PAYABLE_STATUSES = List.of(
            PrincipalRepaymentLedger.STATUS_UPCOMING,
            PrincipalRepaymentLedger.STATUS_PARTIAL,
            PrincipalRepaymentLedger.STATUS_OVERDUE
    );

    @Bean
    public Job principalAutoPaymentJob(JobRepository jobRepository, Step principalAutoPaymentStep) {
        return new JobBuilder("principalAutoPaymentJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(principalAutoPaymentStep)
                .build();
    }

    @Bean
    public Step principalAutoPaymentStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaCursorItemReader<PrincipalRepaymentLedger> principalAutoPaymentReader,
            ItemProcessor<PrincipalRepaymentLedger, PrincipalRepaymentLedger> principalAutoPaymentProcessor,
            ItemWriter<PrincipalRepaymentLedger> principalAutoPaymentWriter,
            StepExecutionListener principalAutoPaymentStepLogger
    ) {
        return new StepBuilder("principalAutoPaymentStep", jobRepository)
                // 원금 자동 상환은 지갑 차감, 원장 갱신, 한도 사용액 감소가 함께 일어나는 금융성 처리다.
                // 한 건 실패가 다른 원장 처리까지 롤백하지 않도록 원장 1건 단위로 commit/rollback한다.
                .<PrincipalRepaymentLedger, PrincipalRepaymentLedger>chunk(CHUNK_SIZE, transactionManager)
                .reader(principalAutoPaymentReader)
                .processor(principalAutoPaymentProcessor)
                .writer(principalAutoPaymentWriter)
                .listener(principalAutoPaymentStepLogger)
                .build();
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<PrincipalRepaymentLedger> principalAutoPaymentReader(
            EntityManagerFactory entityManagerFactory,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);

        // Cursor reader를 사용해 처리 중 상태/납부 금액이 바뀌어도 offset paging 누락이 생기지 않게 한다.
        return new JpaCursorItemReaderBuilder<PrincipalRepaymentLedger>()
                .name("principalAutoPaymentReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT p
                        FROM PrincipalAutoPaymentLedger p
                        WHERE p.dueDate <= :today
                          AND p.status IN :statuses
                          AND p.amountPaid < p.principalAmount
                        ORDER BY p.dueDate, p.id
                        """)
                .parameterValues(Map.of(
                        "today", today,
                        "statuses", PAYABLE_STATUSES
                ))
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<PrincipalRepaymentLedger, PrincipalRepaymentLedger> principalAutoPaymentProcessor(
            PrincipalAutoPaymentService principalAutoPaymentService,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);
        LocalDateTime now = LocalDateTime.now(batchClock);

        // Reader가 읽은 원장을 그대로 믿지 않고 id만 넘겨 서비스에서 잠금 조회와 최신 상태 검증을 수행한다.
        return principalLedger -> principalAutoPaymentService.payAutomatically(principalLedger.getId(), today, now)
                .orElse(null);
    }

    @Bean
    public ItemWriter<PrincipalRepaymentLedger> principalAutoPaymentWriter() {
        // 실제 저장은 Processor가 호출한 서비스 트랜잭션 안에서 끝난다.
        return chunk -> {
        };
    }

    @Bean
    public StepExecutionListener principalAutoPaymentStepLogger() {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                log.info("자동 원금 상환 Step을 시작합니다. stepName={}", stepExecution.getStepName());
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                log.info(
                        "자동 원금 상환 Step이 종료되었습니다. stepName={}, status={}, readCount={}, writeCount={}, filterCount={}, skipCount={}",
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
            throw new IllegalArgumentException("자동 원금 상환 배치의 targetDate JobParameter 값이 올바르지 않습니다.", ex);
        }
    }
}
