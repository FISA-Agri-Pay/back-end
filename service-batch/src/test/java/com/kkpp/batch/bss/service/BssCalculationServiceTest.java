package com.kkpp.batch.bss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.kkpp.batch.bss.domain.CreditLimit;
import com.kkpp.batch.bss.domain.InterestLedger;
import com.kkpp.batch.bss.domain.LoanOverdueLedger;
import com.kkpp.batch.bss.domain.PrincipalRepaymentLedger;
import com.kkpp.batch.bss.dto.BssCalculationResult;
import com.kkpp.batch.bss.repository.InterestLedgerRepository;
import com.kkpp.batch.bss.repository.LoanOverdueLedgerRepository;
import com.kkpp.batch.bss.repository.PrincipalRepaymentLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BssCalculationServiceTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 5);
    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 6, 1, 1, 0);
    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111148");
    private static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333348");
    private static final UUID APPLICATION_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222248");
    private static final UUID INTEREST_LEDGER_PUBLIC_ID = UUID.fromString("77777777-7777-4777-8777-777777777748");
    private static final UUID PRINCIPAL_REPAYMENT_PUBLIC_ID = UUID.fromString("88888888-8888-4888-8888-888888888848");
    private static final UUID ORDER_PUBLIC_ID = UUID.fromString("55555555-5555-4555-8555-555555555548");
    private static final UUID PAYMENT_REQUEST_PUBLIC_ID = UUID.fromString("66666666-6666-4666-8666-666666666648");

    private InterestLedgerRepository interestLedgerRepository;
    private PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private LoanOverdueLedgerRepository loanOverdueLedgerRepository;
    private BssCalculationService service;

    @BeforeEach
    void setUp() {
        interestLedgerRepository = Mockito.mock(InterestLedgerRepository.class);
        principalRepaymentLedgerRepository = Mockito.mock(PrincipalRepaymentLedgerRepository.class);
        loanOverdueLedgerRepository = Mockito.mock(LoanOverdueLedgerRepository.class);
        service = new BssCalculationService(
                interestLedgerRepository,
                principalRepaymentLedgerRepository,
                loanOverdueLedgerRepository
        );

        when(interestLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                any(), any(), any()))
                .thenReturn(List.of());
        when(principalRepaymentLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                any(), any(), any()))
                .thenReturn(List.of());
        when(loanOverdueLedgerRepository.findMonthlyOverdues(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void calculateGivesNeutralRepaymentScoresWhenNoLedgerExists() throws Exception {
        BssCalculationResult result = calculate(
                creditLimit(1L, 1L, new BigDecimal("1000000"), new BigDecimal("900000"))
        );

        assertThat(result.repaymentScore()).isEqualTo(24);
        assertThat(result.overdueScore()).isEqualTo(40);
        assertThat(result.usageScore()).isEqualTo(20);
        assertThat(result.totalScore()).isEqualTo(84);
    }

    @Test
    void calculateInterestPaymentScoreByPaymentRate() throws Exception {
        assertInterestRepaymentScore(new BigDecimal("950.00"), 25);
        assertInterestRepaymentScore(new BigDecimal("800.00"), 18);
        assertInterestRepaymentScore(new BigDecimal("600.00"), 10);
        assertInterestRepaymentScore(new BigDecimal("599.00"), 5);
    }

    @Test
    void calculatePrincipalPaymentScoreByPaymentRate() throws Exception {
        assertPrincipalRepaymentScore(new BigDecimal("950.00"), 15);
        assertPrincipalRepaymentScore(new BigDecimal("800.00"), 10);
        assertPrincipalRepaymentScore(new BigDecimal("600.00"), 6);
        assertPrincipalRepaymentScore(new BigDecimal("599.00"), 3);
    }

    @Test
    void calculateOverdueScoreIsFortyWhenNoOverdueExists() throws Exception {
        BssCalculationResult result = calculate(defaultCreditLimit());

        assertThat(result.overdueScore()).isEqualTo(40);
    }

    @Test
    void calculateOverdueScoreIsZeroWhenUnresolvedOverdueExists() throws Exception {
        when(loanOverdueLedgerRepository.findMonthlyOverdues(eq(CREDIT_LIMIT_PUBLIC_ID), any(), any()))
                .thenReturn(List.of(overdue(1L, null, 3)));

        BssCalculationResult result = calculate(defaultCreditLimit());

        assertThat(result.overdueScore()).isEqualTo(0);
    }

    @Test
    void calculateOverdueScoreUsesResolvedOverdueHistory() throws Exception {
        when(loanOverdueLedgerRepository.findMonthlyOverdues(eq(CREDIT_LIMIT_PUBLIC_ID), any(), any()))
                .thenReturn(List.of(overdue(1L, LocalDateTime.of(2026, 5, 10, 1, 0), 5)));

        BssCalculationResult result = calculate(defaultCreditLimit());

        assertThat(result.overdueScore()).isEqualTo(30);
    }

    @Test
    void calculateOverdueScoreByCountAndMaxOverdueDays() throws Exception {
        when(loanOverdueLedgerRepository.findMonthlyOverdues(eq(CREDIT_LIMIT_PUBLIC_ID), any(), any()))
                .thenReturn(List.of(
                        overdue(1L, LocalDateTime.of(2026, 5, 10, 1, 0), 8),
                        overdue(2L, LocalDateTime.of(2026, 5, 11, 1, 0), 30)
                ));
        assertThat(calculate(defaultCreditLimit()).overdueScore()).isEqualTo(20);

        when(loanOverdueLedgerRepository.findMonthlyOverdues(eq(CREDIT_LIMIT_PUBLIC_ID), any(), any()))
                .thenReturn(List.of(
                        overdue(1L, LocalDateTime.of(2026, 5, 10, 1, 0), 1),
                        overdue(2L, LocalDateTime.of(2026, 5, 11, 1, 0), 2),
                        overdue(3L, LocalDateTime.of(2026, 5, 12, 1, 0), 3)
                ));
        assertThat(calculate(defaultCreditLimit()).overdueScore()).isEqualTo(10);
    }

    @Test
    void calculateOverdueScoreIsTenWhenResolvedOverdueExceedsThirtyDays() throws Exception {
        when(loanOverdueLedgerRepository.findMonthlyOverdues(eq(CREDIT_LIMIT_PUBLIC_ID), any(), any()))
                .thenReturn(List.of(overdue(1L, LocalDateTime.of(2026, 5, 10, 1, 0), 31)));

        assertThat(calculate(defaultCreditLimit()).overdueScore()).isEqualTo(10);
    }

    @Test
    void calculateUsageScoreByLimitUsageRate() throws Exception {
        assertThat(calculate(creditLimit(1L, 1L, new BigDecimal("1000"), new BigDecimal("900"))).usageScore())
                .isEqualTo(20);
        assertThat(calculate(creditLimit(1L, 1L, new BigDecimal("1000"), new BigDecimal("1000"))).usageScore())
                .isEqualTo(15);
        assertThat(calculate(creditLimit(1L, 1L, new BigDecimal("1000"), new BigDecimal("1001"))).usageScore())
                .isEqualTo(0);
        assertThat(calculate(creditLimit(1L, 1L, BigDecimal.ZERO, new BigDecimal("100"))).usageScore())
                .isEqualTo(0);
    }

    @Test
    void calculateTreatsNullLedgerAmountsAsZero() throws Exception {
        when(interestLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                any(), any(), any()))
                .thenReturn(List.of(interestLedger(null, null)));
        when(principalRepaymentLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                any(), any(), any()))
                .thenReturn(List.of(principalLedger(null, null)));

        BssCalculationResult result = calculate(creditLimit(1L, 1L, new BigDecimal("1000"), null));

        assertThat(result.repaymentScore()).isEqualTo(24);
        assertThat(result.usageScore()).isEqualTo(20);
    }

    @Test
    void calculateReadsLedgersOnlyForTargetMonth() throws Exception {
        CreditLimit creditLimit = defaultCreditLimit();

        calculate(creditLimit);

        verifyMonthlyRange(creditLimit.getPublicId());
    }

    private void assertInterestRepaymentScore(BigDecimal paidAmount, int expectedInterestScore) throws Exception {
        when(interestLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                any(), any(), any()))
                .thenReturn(List.of(interestLedger(new BigDecimal("1000.00"), paidAmount)));

        BssCalculationResult result = calculate(defaultCreditLimit());

        assertThat(result.repaymentScore()).isEqualTo(expectedInterestScore + 9);
    }

    private void assertPrincipalRepaymentScore(BigDecimal paidAmount, int expectedPrincipalScore) throws Exception {
        when(principalRepaymentLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                any(), any(), any()))
                .thenReturn(List.of(principalLedger(new BigDecimal("1000.00"), paidAmount)));

        BssCalculationResult result = calculate(defaultCreditLimit());

        assertThat(result.repaymentScore()).isEqualTo(15 + expectedPrincipalScore);
    }

    private void verifyMonthlyRange(UUID creditLimitPublicId) {
        Mockito.verify(interestLedgerRepository)
                .findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                        creditLimitPublicId,
                        PERIOD.atDay(1),
                        PERIOD.plusMonths(1).atDay(1)
                );
        Mockito.verify(principalRepaymentLedgerRepository)
                .findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                        creditLimitPublicId,
                        PERIOD.atDay(1),
                        PERIOD.plusMonths(1).atDay(1)
                );
        Mockito.verify(loanOverdueLedgerRepository)
                .findMonthlyOverdues(
                        creditLimitPublicId,
                        PERIOD.atDay(1).atStartOfDay(),
                        PERIOD.plusMonths(1).atDay(1).atStartOfDay()
                );
    }

    private BssCalculationResult calculate(CreditLimit creditLimit) {
        return service.calculate(creditLimit, PERIOD, CALCULATED_AT);
    }

    private CreditLimit defaultCreditLimit() throws Exception {
        return creditLimit(1L, 1L, new BigDecimal("1000000"), new BigDecimal("900000"));
    }

    private CreditLimit creditLimit(Long id, Long userId, BigDecimal totalLimit, BigDecimal usedAmount)
            throws Exception {
        CreditLimit creditLimit = newInstance(CreditLimit.class);
        setField(creditLimit, "id", id);
        setField(creditLimit, "publicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(creditLimit, "userPublicId", USER_PUBLIC_ID);
        setField(creditLimit, "applicationPublicId", APPLICATION_PUBLIC_ID);
        setField(creditLimit, "totalLimit", totalLimit);
        setField(creditLimit, "usedAmount", usedAmount);
        setField(creditLimit, "status", "ACTIVE");
        return creditLimit;
    }

    private InterestLedger interestLedger(BigDecimal interestAmount, BigDecimal amountPaid) throws Exception {
        InterestLedger ledger = newInstance(InterestLedger.class);
        setField(ledger, "id", 1L);
        setField(ledger, "publicId", INTEREST_LEDGER_PUBLIC_ID);
        setField(ledger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(ledger, "dueDate", PERIOD.atDay(11));
        setField(ledger, "interestAmount", interestAmount);
        setField(ledger, "amountPaid", amountPaid);
        return ledger;
    }

    private PrincipalRepaymentLedger principalLedger(BigDecimal principalAmount, BigDecimal amountPaid)
            throws Exception {
        PrincipalRepaymentLedger ledger = newInstance(PrincipalRepaymentLedger.class);
        setField(ledger, "id", 1L);
        setField(ledger, "publicId", PRINCIPAL_REPAYMENT_PUBLIC_ID);
        setField(ledger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(ledger, "orderPublicId", ORDER_PUBLIC_ID);
        setField(ledger, "paymentRequestPublicId", PAYMENT_REQUEST_PUBLIC_ID);
        setField(ledger, "dueDate", PERIOD.atDay(20));
        setField(ledger, "principalAmount", principalAmount);
        setField(ledger, "amountPaid", amountPaid);
        return ledger;
    }

    private LoanOverdueLedger overdue(Long id, LocalDateTime resolvedAt, Integer overdueDays) throws Exception {
        LoanOverdueLedger overdue = newInstance(LoanOverdueLedger.class);
        setField(overdue, "id", id);
        setField(overdue, "userPublicId", USER_PUBLIC_ID);
        setField(overdue, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(overdue, "overdueAmount", new BigDecimal("1000.00"));
        setField(overdue, "overdueDays", overdueDays);
        setField(overdue, "resolvedAt", resolvedAt);
        return overdue;
    }

    private <T> T newInstance(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
