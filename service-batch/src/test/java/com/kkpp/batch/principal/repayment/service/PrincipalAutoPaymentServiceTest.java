package com.kkpp.batch.principal.repayment.service;

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

import com.kkpp.batch.interest.payment.domain.Wallet;
import com.kkpp.batch.interest.payment.domain.WalletTransaction;
import com.kkpp.batch.interest.payment.repository.WalletRepository;
import com.kkpp.batch.interest.payment.repository.WalletTransactionRepository;
import com.kkpp.batch.principal.repayment.domain.CreditLimit;
import com.kkpp.batch.principal.repayment.domain.LoanOverdueLedger;
import com.kkpp.batch.principal.repayment.domain.PrincipalRepaymentLedger;
import com.kkpp.batch.principal.repayment.repository.PrincipalRepaymentCreditLimitRepository;
import com.kkpp.batch.principal.repayment.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.batch.principal.repayment.repository.PrincipalRepaymentLoanOverdueLedgerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PrincipalAutoPaymentServiceTest {

    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository =
            Mockito.mock(PrincipalRepaymentLedgerRepository.class);
    private final PrincipalRepaymentCreditLimitRepository principalRepaymentCreditLimitRepository =
            Mockito.mock(PrincipalRepaymentCreditLimitRepository.class);
    private final WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
    private final WalletTransactionRepository walletTransactionRepository =
            Mockito.mock(WalletTransactionRepository.class);
    private final PrincipalRepaymentLoanOverdueLedgerRepository loanOverdueLedgerRepository =
            Mockito.mock(PrincipalRepaymentLoanOverdueLedgerRepository.class);

    private final PrincipalAutoPaymentService service = new PrincipalAutoPaymentService(
            principalRepaymentLedgerRepository,
            principalRepaymentCreditLimitRepository,
            walletRepository,
            walletTransactionRepository,
            loanOverdueLedgerRepository
    );

    @Test
    void payAutomaticallyPaysFullPrincipalWhenWalletBalanceIsEnough() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 1, 0);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                today,
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("10000.00"));
        Wallet wallet = wallet(100L, 1L, new BigDecimal("30000.00"));

        mockLedgerContext(ledger, creditLimit, wallet);

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(wallet.getBalance()).isEqualByComparingTo("20000.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("10000.00");
        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("0.00");
        assertThat(ledger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_PAID);
        assertThat(ledger.getPaidAt()).isEqualTo(now);

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("10000.00");
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("20000.00");
        assertThat(captor.getValue().getTransactionType()).isEqualTo(WalletTransaction.TYPE_PRINCIPAL_PAYMENT);
    }

    @Test
    void payAutomaticallyPartiallyPaysWhenWalletBalanceIsNotEnough() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        LocalDateTime now = LocalDateTime.of(2026, 12, 31, 1, 0);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                today,
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("10000.00"));
        Wallet wallet = wallet(100L, 1L, new BigDecimal("4000.00"));

        mockLedgerContext(ledger, creditLimit, wallet);

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("4000.00");
        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("6000.00");
        assertThat(ledger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_PARTIAL);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallySkipsWhenWalletBalanceIsZero() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                today,
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("10000.00"));
        Wallet wallet = wallet(100L, 1L, BigDecimal.ZERO);

        mockLedgerContext(ledger, creditLimit, wallet);

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(
                1L,
                today,
                LocalDateTime.of(2026, 12, 31, 1, 0)
        );

        assertThat(result).isEmpty();
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("10000.00");
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallySkipsPaidLedger() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                PrincipalRepaymentLedger.STATUS_PAID,
                today,
                LocalDateTime.of(2026, 12, 31, 0, 30)
        );

        when(principalRepaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ledger));

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(
                1L,
                today,
                LocalDateTime.of(2026, 12, 31, 1, 0)
        );

        assertThat(result).isEmpty();
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallySkipsFutureDueDate() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 30);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                LocalDate.of(2026, 12, 31),
                null
        );

        when(principalRepaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ledger));

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(
                1L,
                today,
                LocalDateTime.of(2026, 12, 30, 1, 0)
        );

        assertThat(result).isEmpty();
        verify(walletRepository, never()).findByUserIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallyResolvesOverdueLedgerWhenFullyPaid() throws Exception {
        LocalDate today = LocalDate.of(2027, 1, 10);
        LocalDateTime now = LocalDateTime.of(2027, 1, 10, 1, 0);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("5000.00"),
                PrincipalRepaymentLedger.STATUS_OVERDUE,
                LocalDate.of(2026, 12, 31),
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("5000.00"));
        Wallet wallet = wallet(100L, 1L, new BigDecimal("6000.00"));
        LoanOverdueLedger overdueLedger = overdueLedger(900L, 10L, 1L, new BigDecimal("5000.00"));

        mockLedgerContext(ledger, creditLimit, wallet);
        when(loanOverdueLedgerRepository.findAllByPrincipalRepaymentLedgerIdAndResolvedAtIsNull(1L))
                .thenReturn(List.of(overdueLedger));

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(ledger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_PAID);
        assertThat(overdueLedger.getResolvedAt()).isEqualTo(now);
        assertThat(overdueLedger.getResolvedAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void payAutomaticallyKeepsOverdueStatusWhenPartiallyPaidAfterOverdue() throws Exception {
        LocalDate today = LocalDate.of(2027, 1, 10);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_OVERDUE,
                LocalDate.of(2026, 12, 31),
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("10000.00"));
        Wallet wallet = wallet(100L, 1L, new BigDecimal("4000.00"));

        mockLedgerContext(ledger, creditLimit, wallet);

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(
                1L,
                today,
                LocalDateTime.of(2027, 1, 10, 1, 0)
        );

        assertThat(result).contains(ledger);
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("4000.00");
        assertThat(ledger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_OVERDUE);
        verify(loanOverdueLedgerRepository, never()).findAllByPrincipalRepaymentLedgerIdAndResolvedAtIsNull(any());
    }

    @Test
    void payAutomaticallyDoesNotMakeUsedAmountNegative() throws Exception {
        LocalDate today = LocalDate.of(2026, 12, 31);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                today,
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("3000.00"));
        Wallet wallet = wallet(100L, 1L, new BigDecimal("10000.00"));

        mockLedgerContext(ledger, creditLimit, wallet);

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(
                1L,
                today,
                LocalDateTime.of(2026, 12, 31, 1, 0)
        );

        assertThat(result).contains(ledger);
        assertThat(wallet.getBalance()).isEqualByComparingTo("7000.00");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("3000.00");
        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("0.00");
        assertThat(ledger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_PARTIAL);
    }

    private void mockLedgerContext(PrincipalRepaymentLedger ledger, CreditLimit creditLimit, Wallet wallet) {
        when(principalRepaymentLedgerRepository.findByIdForUpdate(ledger.getId())).thenReturn(Optional.of(ledger));
        when(principalRepaymentCreditLimitRepository.findByIdForUpdate(creditLimit.getId()))
                .thenReturn(Optional.of(creditLimit));
        when(walletRepository.findByUserIdForUpdate(creditLimit.getUserId())).thenReturn(Optional.of(wallet));
    }

    private PrincipalRepaymentLedger principalLedger(
            Long id,
            Long creditLimitId,
            BigDecimal principalAmount,
            BigDecimal amountPaid,
            String status,
            LocalDate dueDate,
            LocalDateTime paidAt
    ) throws Exception {
        PrincipalRepaymentLedger ledger = newInstance(PrincipalRepaymentLedger.class);
        setField(ledger, "id", id);
        setField(ledger, "creditLimitId", creditLimitId);
        setField(ledger, "dueDate", dueDate);
        setField(ledger, "principalAmount", principalAmount);
        setField(ledger, "amountPaid", amountPaid);
        setField(ledger, "paidAt", paidAt);
        setField(ledger, "status", status);
        setField(ledger, "updatedAt", LocalDateTime.of(2026, 5, 1, 0, 0));
        return ledger;
    }

    private CreditLimit creditLimit(Long id, Long userId, BigDecimal usedAmount) throws Exception {
        CreditLimit creditLimit = newInstance(CreditLimit.class);
        setField(creditLimit, "id", id);
        setField(creditLimit, "userId", userId);
        setField(creditLimit, "usedAmount", usedAmount);
        setField(creditLimit, "status", "ACTIVE");
        return creditLimit;
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
            Long principalRepaymentLedgerId,
            BigDecimal overdueAmount
    ) throws Exception {
        LoanOverdueLedger overdueLedger = newInstance(LoanOverdueLedger.class);
        setField(overdueLedger, "id", id);
        setField(overdueLedger, "creditLimitId", creditLimitId);
        setField(overdueLedger, "principalRepaymentLedgerId", principalRepaymentLedgerId);
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
        java.lang.reflect.Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
