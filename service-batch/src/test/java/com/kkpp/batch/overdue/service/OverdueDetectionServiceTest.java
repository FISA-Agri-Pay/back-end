package com.kkpp.batch.overdue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.overdue.domain.LoanOverdueLedger;
import com.kkpp.batch.overdue.repository.OverdueCreditLimitRepository;
import com.kkpp.batch.overdue.repository.OverdueInterestLedgerRepository;
import com.kkpp.batch.overdue.repository.OverdueLoanOverdueLedgerRepository;
import com.kkpp.batch.overdue.repository.OverduePrincipalRepaymentLedgerRepository;
import com.kkpp.batch.principal.repayment.domain.CreditLimit;
import com.kkpp.batch.principal.repayment.domain.PrincipalRepaymentLedger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OverdueDetectionServiceTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111148");
    private static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333348");
    private static final UUID INTEREST_LEDGER_PUBLIC_ID = UUID.fromString("77777777-7777-4777-8777-777777777748");
    private static final UUID PRINCIPAL_REPAYMENT_PUBLIC_ID = UUID.fromString("88888888-8888-4888-8888-888888888848");
    private static final UUID ORDER_PUBLIC_ID = UUID.fromString("55555555-5555-4555-8555-555555555548");
    private static final UUID PAYMENT_REQUEST_PUBLIC_ID = UUID.fromString("66666666-6666-4666-8666-666666666648");

    private final OverdueInterestLedgerRepository interestLedgerRepository =
            Mockito.mock(OverdueInterestLedgerRepository.class);
    private final OverduePrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository =
            Mockito.mock(OverduePrincipalRepaymentLedgerRepository.class);
    private final OverdueCreditLimitRepository creditLimitRepository =
            Mockito.mock(OverdueCreditLimitRepository.class);
    private final OverdueLoanOverdueLedgerRepository loanOverdueLedgerRepository =
            Mockito.mock(OverdueLoanOverdueLedgerRepository.class);

    private final OverdueDetectionService service = new OverdueDetectionService(
            interestLedgerRepository,
            principalRepaymentLedgerRepository,
            creditLimitRepository,
            loanOverdueLedgerRepository
    );

    @Test
    void detectInterestOverdueCreatesOverdueLedger() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 28);
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 1, 0);
        InterestLedger interestLedger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_UPCOMING,
                today.minusDays(1)
        );
        CreditLimit creditLimit = creditLimit(10L, 100L);

        when(interestLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(interestLedger));
        when(creditLimitRepository.findByPublicId(CREDIT_LIMIT_PUBLIC_ID)).thenReturn(Optional.of(creditLimit));
        when(loanOverdueLedgerRepository.findByInterestLedgerPublicIdAndResolvedAtIsNull(INTEREST_LEDGER_PUBLIC_ID))
                .thenReturn(Optional.empty());

        Optional<InterestLedger> result = service.detectInterestOverdue(1L, today, now);

        assertThat(result).contains(interestLedger);
        assertThat(interestLedger.getStatus()).isEqualTo(InterestLedger.STATUS_OVERDUE);

        ArgumentCaptor<LoanOverdueLedger> captor = ArgumentCaptor.forClass(LoanOverdueLedger.class);
        verify(loanOverdueLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getInterestLedgerPublicId()).isEqualTo(INTEREST_LEDGER_PUBLIC_ID);
        assertThat(captor.getValue().getPrincipalRepaymentPublicId()).isNull();
        assertThat(captor.getValue().getUserPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(captor.getValue().getCreditLimitPublicId()).isEqualTo(CREDIT_LIMIT_PUBLIC_ID);
        assertThat(captor.getValue().getOverdueAmount()).isEqualByComparingTo("10000.00");
        assertThat(captor.getValue().getOverdueDays()).isEqualTo(1);
        assertThat(captor.getValue().getStage()).isEqualTo(LoanOverdueLedger.STAGE_ACTIVE);
    }

    @Test
    void detectInterestOverdueUpdatesExistingActiveOverdueLedger() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 28);
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 1, 0);
        InterestLedger interestLedger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("3000.00"),
                InterestLedger.STATUS_PARTIAL,
                today.minusDays(2)
        );
        CreditLimit creditLimit = creditLimit(10L, 100L);
        LoanOverdueLedger existingOverdue = LoanOverdueLedger.interestOverdue(
                USER_PUBLIC_ID,
                CREDIT_LIMIT_PUBLIC_ID,
                INTEREST_LEDGER_PUBLIC_ID,
                new BigDecimal("8000.00"),
                1
        );

        when(interestLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(interestLedger));
        when(creditLimitRepository.findByPublicId(CREDIT_LIMIT_PUBLIC_ID)).thenReturn(Optional.of(creditLimit));
        when(loanOverdueLedgerRepository.findByInterestLedgerPublicIdAndResolvedAtIsNull(INTEREST_LEDGER_PUBLIC_ID))
                .thenReturn(Optional.of(existingOverdue));

        service.detectInterestOverdue(1L, today, now);

        assertThat(interestLedger.getStatus()).isEqualTo(InterestLedger.STATUS_OVERDUE);
        assertThat(existingOverdue.getOverdueAmount()).isEqualByComparingTo("7000.00");
        assertThat(existingOverdue.getOverdueDays()).isEqualTo(2);
        verify(loanOverdueLedgerRepository, never()).save(Mockito.any());
    }

    @Test
    void detectPrincipalOverdueCreatesOverdueLedger() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 1, 0);
        PrincipalRepaymentLedger principalLedger = principalLedger(
                2L,
                20L,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                today.minusDays(3)
        );
        CreditLimit creditLimit = creditLimit(20L, 200L);

        when(principalRepaymentLedgerRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(principalLedger));
        when(creditLimitRepository.findByPublicId(CREDIT_LIMIT_PUBLIC_ID)).thenReturn(Optional.of(creditLimit));
        when(loanOverdueLedgerRepository.findByPrincipalRepaymentPublicIdAndResolvedAtIsNull(PRINCIPAL_REPAYMENT_PUBLIC_ID))
                .thenReturn(Optional.empty());

        Optional<PrincipalRepaymentLedger> result = service.detectPrincipalOverdue(2L, today, now);

        assertThat(result).contains(principalLedger);
        assertThat(principalLedger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_OVERDUE);

        ArgumentCaptor<LoanOverdueLedger> captor = ArgumentCaptor.forClass(LoanOverdueLedger.class);
        verify(loanOverdueLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getInterestLedgerPublicId()).isNull();
        assertThat(captor.getValue().getPrincipalRepaymentPublicId()).isEqualTo(PRINCIPAL_REPAYMENT_PUBLIC_ID);
        assertThat(captor.getValue().getUserPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(captor.getValue().getCreditLimitPublicId()).isEqualTo(CREDIT_LIMIT_PUBLIC_ID);
        assertThat(captor.getValue().getOverdueAmount()).isEqualByComparingTo("50000.00");
        assertThat(captor.getValue().getOverdueDays()).isEqualTo(3);
    }

    @Test
    void detectPrincipalOverdueSkipsLedgerDueToday() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 1, 0);
        PrincipalRepaymentLedger principalLedger = principalLedger(
                2L,
                20L,
                new BigDecimal("50000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                today
        );

        when(principalRepaymentLedgerRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(principalLedger));

        Optional<PrincipalRepaymentLedger> result = service.detectPrincipalOverdue(2L, today, now);

        assertThat(result).isEmpty();
        assertThat(principalLedger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_UPCOMING);
        verify(creditLimitRepository, never()).findByPublicId(Mockito.any());
        verify(loanOverdueLedgerRepository, never()).save(Mockito.any());
    }

    @Test
    void detectInterestOverdueSkipsPaidLedger() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 28);
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 1, 0);
        InterestLedger interestLedger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                InterestLedger.STATUS_PAID,
                today.minusDays(1)
        );

        when(interestLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(interestLedger));

        Optional<InterestLedger> result = service.detectInterestOverdue(1L, today, now);

        assertThat(result).isEmpty();
        assertThat(interestLedger.getStatus()).isEqualTo(InterestLedger.STATUS_PAID);
        verify(creditLimitRepository, never()).findByPublicId(Mockito.any());
        verify(loanOverdueLedgerRepository, never()).save(Mockito.any());
    }

    @Test
    void detectPrincipalOverdueDoesNotDuplicateExistingActiveOverdueLedger() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 1, 0);
        PrincipalRepaymentLedger principalLedger = principalLedger(
                2L,
                20L,
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                PrincipalRepaymentLedger.STATUS_PARTIAL,
                today.minusDays(4)
        );
        CreditLimit creditLimit = creditLimit(20L, 200L);
        LoanOverdueLedger existingOverdue = LoanOverdueLedger.principalOverdue(
                USER_PUBLIC_ID,
                CREDIT_LIMIT_PUBLIC_ID,
                PRINCIPAL_REPAYMENT_PUBLIC_ID,
                new BigDecimal("45000.00"),
                2
        );

        when(principalRepaymentLedgerRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(principalLedger));
        when(creditLimitRepository.findByPublicId(CREDIT_LIMIT_PUBLIC_ID)).thenReturn(Optional.of(creditLimit));
        when(loanOverdueLedgerRepository.findByPrincipalRepaymentPublicIdAndResolvedAtIsNull(PRINCIPAL_REPAYMENT_PUBLIC_ID))
                .thenReturn(Optional.of(existingOverdue));

        service.detectPrincipalOverdue(2L, today, now);

        assertThat(existingOverdue.getOverdueAmount()).isEqualByComparingTo("40000.00");
        assertThat(existingOverdue.getOverdueDays()).isEqualTo(4);
        verify(loanOverdueLedgerRepository, never()).save(Mockito.any());
    }

    private InterestLedger interestLedger(
            Long id,
            Long creditLimitId,
            BigDecimal interestAmount,
            BigDecimal amountPaid,
            String status,
            LocalDate dueDate
    ) throws Exception {
        InterestLedger ledger = newInstance(InterestLedger.class);
        setField(ledger, "id", id);
        setField(ledger, "publicId", INTEREST_LEDGER_PUBLIC_ID);
        setField(ledger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(ledger, "basePrincipal", new BigDecimal("1000000.00"));
        setField(ledger, "dueDate", dueDate);
        setField(ledger, "interestAmount", interestAmount);
        setField(ledger, "amountPaid", amountPaid);
        setField(ledger, "status", status);
        setField(ledger, "createdAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        setField(ledger, "updatedAt", LocalDateTime.of(2026, 1, 1, 0, 0));
        return ledger;
    }

    private PrincipalRepaymentLedger principalLedger(
            Long id,
            Long creditLimitId,
            BigDecimal principalAmount,
            BigDecimal amountPaid,
            String status,
            LocalDate dueDate
    ) throws Exception {
        PrincipalRepaymentLedger ledger = newInstance(PrincipalRepaymentLedger.class);
        setField(ledger, "id", id);
        setField(ledger, "publicId", PRINCIPAL_REPAYMENT_PUBLIC_ID);
        setField(ledger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(ledger, "orderPublicId", ORDER_PUBLIC_ID);
        setField(ledger, "paymentRequestPublicId", PAYMENT_REQUEST_PUBLIC_ID);
        setField(ledger, "dueDate", dueDate);
        setField(ledger, "principalAmount", principalAmount);
        setField(ledger, "amountPaid", amountPaid);
        setField(ledger, "status", status);
        return ledger;
    }

    private CreditLimit creditLimit(Long id, Long userId) throws Exception {
        CreditLimit creditLimit = newInstance(CreditLimit.class);
        setField(creditLimit, "id", id);
        setField(creditLimit, "publicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(creditLimit, "userPublicId", USER_PUBLIC_ID);
        setField(creditLimit, "usedAmount", new BigDecimal("100000.00"));
        setField(creditLimit, "status", "ACTIVE");
        return creditLimit;
    }

    private <T> T newInstance(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
