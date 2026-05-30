package com.kkpp.batch.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.interest.domain.CreditLimit;
import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.interest.repository.InterestLedgerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InterestChargeServiceTest {

    private static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333348");
    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111148");
    private static final UUID APPLICATION_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222248");

    private final InterestLedgerRepository interestLedgerRepository =
            Mockito.mock(InterestLedgerRepository.class);
    private final InterestChargeService service = new InterestChargeService(interestLedgerRepository);

    @Test
    void createMonthlyInterestLedgerCreatesLedgerForActiveUsedCreditLimit() throws Exception {
        CreditLimit creditLimit = creditLimit(
                1L,
                new BigDecimal("1200000.00"),
                new BigDecimal("0.1200"),
                13,
                LocalDate.of(2026, 12, 31)
        );
        YearMonth targetMonth = YearMonth.of(2026, 5);
        LocalDate dueDate = LocalDate.of(2026, 5, 13);
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 3, 0);

        when(interestLedgerRepository.existsByCreditLimitPublicIdAndDueDate(CREDIT_LIMIT_PUBLIC_ID, dueDate))
                .thenReturn(false);

        Optional<InterestLedger> result = service.createMonthlyInterestLedger(creditLimit, targetMonth, now);

        assertThat(result).isPresent();
        InterestLedger ledger = result.get();
        assertThat(ledger.getCreditLimitPublicId()).isEqualTo(CREDIT_LIMIT_PUBLIC_ID);
        assertThat(ledger.getBasePrincipal()).isEqualByComparingTo("1200000.00");
        assertThat(ledger.getInterestAmount()).isEqualByComparingTo("12000.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(ledger.getStatus()).isEqualTo(InterestLedger.STATUS_UPCOMING);
        assertThat(ledger.getDueDate()).isEqualTo(dueDate);
        assertThat(ledger.getCreatedAt()).isEqualTo(now);
        assertThat(ledger.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void createMonthlyInterestLedgerSkipsZeroUsedAmount() throws Exception {
        CreditLimit creditLimit = creditLimit(
                1L,
                BigDecimal.ZERO,
                new BigDecimal("0.1200"),
                13,
                LocalDate.of(2026, 12, 31)
        );

        Optional<InterestLedger> result = service.createMonthlyInterestLedger(
                creditLimit,
                YearMonth.of(2026, 5),
                LocalDateTime.of(2026, 5, 1, 3, 0)
        );

        assertThat(result).isEmpty();
        verify(interestLedgerRepository, never())
                .existsByCreditLimitPublicIdAndDueDate(CREDIT_LIMIT_PUBLIC_ID, LocalDate.of(2026, 5, 13));
    }

    @Test
    void createMonthlyInterestLedgerSkipsInactiveCreditLimit() throws Exception {
        CreditLimit creditLimit = creditLimit(
                1L,
                new BigDecimal("1200000.00"),
                new BigDecimal("0.1200"),
                13,
                LocalDate.of(2026, 12, 31),
                "SUSPENDED"
        );

        Optional<InterestLedger> result = service.createMonthlyInterestLedger(
                creditLimit,
                YearMonth.of(2026, 5),
                LocalDateTime.of(2026, 5, 1, 3, 0)
        );

        assertThat(result).isEmpty();
        verify(interestLedgerRepository, never())
                .existsByCreditLimitPublicIdAndDueDate(CREDIT_LIMIT_PUBLIC_ID, LocalDate.of(2026, 5, 13));
    }

    @Test
    void createMonthlyInterestLedgerSkipsDuplicateDueDate() throws Exception {
        CreditLimit creditLimit = creditLimit(
                1L,
                new BigDecimal("1200000.00"),
                new BigDecimal("0.1200"),
                13,
                LocalDate.of(2026, 12, 31)
        );
        LocalDate dueDate = LocalDate.of(2026, 5, 13);

        when(interestLedgerRepository.existsByCreditLimitPublicIdAndDueDate(CREDIT_LIMIT_PUBLIC_ID, dueDate))
                .thenReturn(true);

        Optional<InterestLedger> result = service.createMonthlyInterestLedger(
                creditLimit,
                YearMonth.of(2026, 5),
                LocalDateTime.of(2026, 5, 1, 3, 0)
        );

        assertThat(result).isEmpty();
    }

    @Test
    void createMonthlyInterestLedgerSkipsAfterPrincipalDueDate() throws Exception {
        CreditLimit creditLimit = creditLimit(
                1L,
                new BigDecimal("1200000.00"),
                new BigDecimal("0.1200"),
                11,
                LocalDate.of(2026, 12, 31)
        );

        Optional<InterestLedger> result = service.createMonthlyInterestLedger(
                creditLimit,
                YearMonth.of(2027, 1),
                LocalDateTime.of(2027, 1, 1, 3, 0)
        );

        assertThat(result).isEmpty();
        verify(interestLedgerRepository, never())
                .existsByCreditLimitPublicIdAndDueDate(CREDIT_LIMIT_PUBLIC_ID, LocalDate.of(2027, 1, 11));
    }

    @Test
    void createMonthlyInterestLedgerThrowsWhenInterestDueDayIsInvalid() throws Exception {
        CreditLimit creditLimit = creditLimit(
                1L,
                new BigDecimal("1200000.00"),
                new BigDecimal("0.1200"),
                31,
                LocalDate.of(2026, 12, 31)
        );

        assertThatThrownBy(() -> service.createMonthlyInterestLedger(
                creditLimit,
                YearMonth.of(2026, 5),
                LocalDateTime.of(2026, 5, 1, 3, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interest_due_day는 1~28 사이여야 합니다");
    }

    @Test
    void createMonthlyInterestLedgerThrowsWhenPrincipalDueDateIsMissing() throws Exception {
        CreditLimit creditLimit = creditLimit(
                1L,
                new BigDecimal("1200000.00"),
                new BigDecimal("0.1200"),
                13,
                null
        );

        assertThatThrownBy(() -> service.createMonthlyInterestLedger(
                creditLimit,
                YearMonth.of(2026, 5),
                LocalDateTime.of(2026, 5, 1, 3, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("principal_due_date가 없어 이자 원장을 생성할 수 없습니다");
    }

    @Test
    void calculateDefaultInterestDueDayCapsAtTwentyEight() {
        assertThat(CreditLimit.calculateDefaultInterestDueDay(LocalDate.of(2026, 5, 3))).isEqualTo(13);
        assertThat(CreditLimit.calculateDefaultInterestDueDay(LocalDate.of(2026, 5, 15))).isEqualTo(25);
        assertThat(CreditLimit.calculateDefaultInterestDueDay(LocalDate.of(2026, 5, 25))).isEqualTo(28);
    }

    private CreditLimit creditLimit(
            Long id,
            BigDecimal usedAmount,
            BigDecimal interestRate,
            Integer interestDueDay,
            LocalDate principalDueDate
    ) throws Exception {
        return creditLimit(id, usedAmount, interestRate, interestDueDay, principalDueDate, "ACTIVE");
    }

    private CreditLimit creditLimit(
            Long id,
            BigDecimal usedAmount,
            BigDecimal interestRate,
            Integer interestDueDay,
            LocalDate principalDueDate,
            String status
    ) throws Exception {
        Constructor<CreditLimit> constructor = CreditLimit.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CreditLimit creditLimit = constructor.newInstance();
        setField(creditLimit, "id", id);
        setField(creditLimit, "publicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(creditLimit, "userPublicId", USER_PUBLIC_ID);
        setField(creditLimit, "applicationPublicId", APPLICATION_PUBLIC_ID);
        setField(creditLimit, "cropTypeSnapshot", "RICE");
        setField(creditLimit, "usedAmount", usedAmount);
        setField(creditLimit, "interestRate", interestRate);
        setField(creditLimit, "interestDueDay", interestDueDay);
        setField(creditLimit, "principalDueDate", principalDueDate);
        setField(creditLimit, "status", status);
        return creditLimit;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
