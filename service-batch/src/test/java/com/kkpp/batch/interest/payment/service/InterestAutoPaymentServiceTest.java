package com.kkpp.batch.interest.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.kkpp.batch.interest.domain.CreditLimit;
import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.interest.payment.domain.LoanOverdueLedger;
import com.kkpp.batch.interest.payment.domain.Wallet;
import com.kkpp.batch.interest.payment.domain.WalletTransaction;
import com.kkpp.batch.interest.payment.repository.InterestPaymentCreditLimitRepository;
import com.kkpp.batch.interest.payment.repository.InterestPaymentLedgerRepository;
import com.kkpp.batch.interest.payment.repository.LoanOverdueLedgerRepository;
import com.kkpp.batch.interest.payment.repository.WalletRepository;
import com.kkpp.batch.interest.payment.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class InterestAutoPaymentServiceTest {

    private final InterestPaymentLedgerRepository interestPaymentLedgerRepository =
            Mockito.mock(InterestPaymentLedgerRepository.class);
    private final InterestPaymentCreditLimitRepository interestPaymentCreditLimitRepository =
            Mockito.mock(InterestPaymentCreditLimitRepository.class);
    private final WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
    private final WalletTransactionRepository walletTransactionRepository =
            Mockito.mock(WalletTransactionRepository.class);
    private final LoanOverdueLedgerRepository loanOverdueLedgerRepository =
            Mockito.mock(LoanOverdueLedgerRepository.class);

    private final InterestAutoPaymentService service = new InterestAutoPaymentService(
            interestPaymentLedgerRepository,
            interestPaymentCreditLimitRepository,
            walletRepository,
            walletTransactionRepository,
            loanOverdueLedgerRepository
    );

    @Test
    void payAutomaticallyPaysFullAmountWhenWalletBalanceIsEnough() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 11);
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 1, 0);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_UPCOMING,
                today,
                null
        );
        Wallet wallet = wallet(100L, 1L, new BigDecimal("30000.00"));

        mockLedgerContext(ledger, creditLimit(10L, 1L), wallet);

        Optional<InterestLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(wallet.getBalance()).isEqualByComparingTo("20000.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("10000.00");
        assertThat(ledger.getStatus()).isEqualTo(InterestLedger.STATUS_PAID);
        assertThat(ledger.getPaidAt()).isEqualTo(now);

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("10000.00");
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("20000.00");
        assertThat(captor.getValue().getTransactionType()).isEqualTo(WalletTransaction.TYPE_INTEREST_PAYMENT);
    }

    @Test
    void payAutomaticallyPartiallyPaysWhenWalletBalanceIsNotEnough() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 11);
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 1, 0);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_UPCOMING,
                today,
                null
        );
        Wallet wallet = wallet(100L, 1L, new BigDecimal("4000.00"));

        mockLedgerContext(ledger, creditLimit(10L, 1L), wallet);

        Optional<InterestLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("4000.00");
        assertThat(ledger.getStatus()).isEqualTo(InterestLedger.STATUS_PARTIAL);
        assertThat(ledger.getPaidAt()).isNull();
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallySkipsWhenWalletBalanceIsZero() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 11);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_UPCOMING,
                today,
                null
        );
        Wallet wallet = wallet(100L, 1L, BigDecimal.ZERO);

        mockLedgerContext(ledger, creditLimit(10L, 1L), wallet);

        Optional<InterestLedger> result = service.payAutomatically(1L, today, LocalDateTime.of(2026, 5, 11, 1, 0));

        assertThat(result).isEmpty();
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("0.00");
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallySkipsPaidLedger() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 11);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                InterestLedger.STATUS_PAID,
                today,
                LocalDateTime.of(2026, 5, 11, 0, 30)
        );

        when(interestPaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ledger));

        Optional<InterestLedger> result = service.payAutomatically(1L, today, LocalDateTime.of(2026, 5, 11, 1, 0));

        assertThat(result).isEmpty();
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallySkipsFutureDueDate() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 10);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_UPCOMING,
                LocalDate.of(2026, 5, 11),
                null
        );

        when(interestPaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ledger));

        Optional<InterestLedger> result = service.payAutomatically(1L, today, LocalDateTime.of(2026, 5, 10, 1, 0));

        assertThat(result).isEmpty();
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallyResolvesOverdueLedgerWhenFullyPaid() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 20);
        LocalDateTime now = LocalDateTime.of(2026, 5, 20, 1, 0);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("5000.00"),
                InterestLedger.STATUS_OVERDUE,
                LocalDate.of(2026, 5, 11),
                null
        );
        Wallet wallet = wallet(100L, 1L, new BigDecimal("6000.00"));
        LoanOverdueLedger overdueLedger = overdueLedger(900L, 10L, 1L, new BigDecimal("5000.00"));

        mockLedgerContext(ledger, creditLimit(10L, 1L), wallet);
        when(loanOverdueLedgerRepository.findAllByInterestLedgerIdAndResolvedAtIsNull(1L))
                .thenReturn(List.of(overdueLedger));

        Optional<InterestLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(ledger.getStatus()).isEqualTo(InterestLedger.STATUS_PAID);
        assertThat(overdueLedger.getResolvedAt()).isEqualTo(now);
        assertThat(overdueLedger.getResolvedAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void payAutomaticallyKeepsOverdueStatusWhenPartiallyPaidAfterOverdue() throws Exception {
        LocalDate today = LocalDate.of(2026, 5, 20);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_OVERDUE,
                LocalDate.of(2026, 5, 11),
                null
        );
        Wallet wallet = wallet(100L, 1L, new BigDecimal("4000.00"));

        mockLedgerContext(ledger, creditLimit(10L, 1L), wallet);

        Optional<InterestLedger> result = service.payAutomatically(
                1L,
                today,
                LocalDateTime.of(2026, 5, 20, 1, 0)
        );

        assertThat(result).contains(ledger);
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("4000.00");
        assertThat(ledger.getStatus()).isEqualTo(InterestLedger.STATUS_OVERDUE);
    }

    private void mockLedgerContext(InterestLedger ledger, CreditLimit creditLimit, Wallet wallet) {
        when(interestPaymentLedgerRepository.findByIdForUpdate(ledger.getId())).thenReturn(Optional.of(ledger));
        when(interestPaymentCreditLimitRepository.findById(creditLimit.getId())).thenReturn(Optional.of(creditLimit));
        when(walletRepository.findByUserIdForUpdate(creditLimit.getUserId())).thenReturn(Optional.of(wallet));
    }

    private CreditLimit creditLimit(Long id, Long userId) throws Exception {
        CreditLimit creditLimit = newInstance(CreditLimit.class);
        setField(creditLimit, "id", id);
        setField(creditLimit, "userId", userId);
        setField(creditLimit, "status", "ACTIVE");
        return creditLimit;
    }

    private InterestLedger interestLedger(
            Long id,
            Long creditLimitId,
            BigDecimal interestAmount,
            BigDecimal amountPaid,
            String status,
            LocalDate dueDate,
            LocalDateTime paidAt
    ) throws Exception {
        InterestLedger ledger = newInstance(InterestLedger.class);
        setField(ledger, "id", id);
        setField(ledger, "creditLimitId", creditLimitId);
        setField(ledger, "basePrincipal", new BigDecimal("1000000.00"));
        setField(ledger, "dueDate", dueDate);
        setField(ledger, "interestAmount", interestAmount);
        setField(ledger, "amountPaid", amountPaid);
        setField(ledger, "paidAt", paidAt);
        setField(ledger, "status", status);
        setField(ledger, "createdAt", LocalDateTime.of(2026, 5, 1, 0, 0));
        setField(ledger, "updatedAt", LocalDateTime.of(2026, 5, 1, 0, 0));
        return ledger;
    }

    private Wallet wallet(Long id, Long userId, BigDecimal balance) throws Exception {
        Wallet wallet = newInstance(Wallet.class);
        setField(wallet, "id", id);
        setField(wallet, "userId", userId);
        setField(wallet, "balance", balance);
        setField(wallet, "status", "ACTIVE");
        setField(wallet, "updatedAt", LocalDateTime.of(2026, 5, 1, 0, 0));
        return wallet;
    }

    private LoanOverdueLedger overdueLedger(
            Long id,
            Long creditLimitId,
            Long interestLedgerId,
            BigDecimal overdueAmount
    ) throws Exception {
        LoanOverdueLedger overdueLedger = newInstance(LoanOverdueLedger.class);
        setField(overdueLedger, "id", id);
        setField(overdueLedger, "creditLimitId", creditLimitId);
        setField(overdueLedger, "interestLedgerId", interestLedgerId);
        setField(overdueLedger, "overdueAmount", overdueAmount);
        setField(overdueLedger, "updatedAt", LocalDateTime.of(2026, 5, 1, 0, 0));
        return overdueLedger;
    }

    private <T> T newInstance(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
