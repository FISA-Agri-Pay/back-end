package com.kkpp.batch.interest.payment.job;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.interest.payment.service.InterestAutoPaymentService;
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
public class InterestAutoPaymentJobConfig {

    private static final int CHUNK_SIZE = 100;
    // 납부일이 도래했거나 지난 미납 이자 원장을 대상으로 지갑 자동 차감을 수행하는 Job이다.
    @Bean
    public Job interestAutoPaymentJob(JobRepository jobRepository, Step interestAutoPaymentStep) {
        return new JobBuilder("interestAutoPaymentJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(interestAutoPaymentStep)
                .build();
    }

    @Bean
    public Step interestAutoPaymentStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JpaPagingItemReader<InterestLedger> interestAutoPaymentReader,
            ItemProcessor<InterestLedger, InterestLedger> interestAutoPaymentProcessor,
            ItemWriter<InterestLedger> interestAutoPaymentWriter
    ) {
        return new StepBuilder("interestAutoPaymentStep", jobRepository)
                // 이자 원장 생성 배치와 동일하게 100건 단위로 처리한다.
                .<InterestLedger, InterestLedger>chunk(CHUNK_SIZE, transactionManager)
                .reader(interestAutoPaymentReader)
                .processor(interestAutoPaymentProcessor)
                .writer(interestAutoPaymentWriter)
                .build();
    }

    // Reader는 자동 상환 후보만 조회한다.
    // 실제 차감 가능 금액은 지갑 잔액과 최신 원장 상태를 다시 확인해야 하므로 서비스에서 계산한다.
    @Bean
    @StepScope
    public JpaPagingItemReader<InterestLedger> interestAutoPaymentReader(
            EntityManagerFactory entityManagerFactory,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);

        // status와 amountPaid는 자동 상환 중 바뀌는 값이다.
        // JpaPagingItemReader는 offset paging을 쓰므로, 바뀌는 컬럼을 조회 조건에 넣으면 다음 페이지에서 일부 원장이 건너뛰어질 수 있다.
        // 그래서 Reader는 납부 예정일만으로 안정적인 후보 목록을 읽고, 실제 처리 가능 여부는 서비스에서 최신 상태로 다시 판단한다.
        return new JpaPagingItemReaderBuilder<InterestLedger>()
                .name("interestAutoPaymentReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("""
                        SELECT i
                        FROM InterestChargeLedger i
                        WHERE i.dueDate <= :today
                        ORDER BY i.dueDate, i.id
                        """)
                .parameterValues(Map.of(
                        "today", today
                ))
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<InterestLedger, InterestLedger> interestAutoPaymentProcessor(
            InterestAutoPaymentService interestAutoPaymentService,
            Clock batchClock,
            @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate today = resolveTargetDate(targetDate, batchClock);
        LocalDateTime now = LocalDateTime.now(batchClock);

        // Reader가 읽은 엔티티를 그대로 수정하지 않고 id만 서비스로 넘긴다.
        // 서비스에서 원장과 지갑을 잠금 조회한 뒤 최신 미납 금액 기준으로 자동 상환을 처리한다.
        return interestLedger -> interestAutoPaymentService.payAutomatically(interestLedger.getId(), today, now)
                .orElse(null);
    }

    @Bean
    public ItemWriter<InterestLedger> interestAutoPaymentWriter() {
        // 실제 저장은 Processor가 호출한 서비스 트랜잭션 안에서 끝난다.
        // 지갑 차감, 이자 원장 갱신, 거래 이력 저장, 연체 해소가 한 트랜잭션으로 묶여야 하므로 Writer는 비워둔다.
        return chunk -> {
        };
    }

    private LocalDate resolveTargetDate(String targetDate, Clock batchClock) {
        // targetDate는 운영자가 입력하는 값이라기보다 CronJob/수동 배치 실행 시 넘기는 JobParameter다.
        // 값이 없으면 배치 실행일을 기준일로 사용한다.
        if (targetDate == null || targetDate.isBlank()) {
            return LocalDate.now(batchClock);
        }

        try {
            return LocalDate.parse(targetDate);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("자동 이자 상환 배치의 targetDate JobParameter 값이 올바르지 않습니다.", ex);
        }
    }
}
