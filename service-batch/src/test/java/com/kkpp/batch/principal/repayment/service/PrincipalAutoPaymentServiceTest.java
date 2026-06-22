package com.kkpp.batch.principal.repayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.UUID;

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

    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111148");
    private static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333348");
    private static final UUID PRINCIPAL_REPAYMENT_PUBLIC_ID = UUID.fromString("88888888-8888-4888-8888-888888888848");
    private static final UUID ORDER_PUBLIC_ID = UUID.fromString("55555555-5555-4555-8555-555555555548");
    private static final UUID PAYMENT_REQUEST_PUBLIC_ID = UUID.fromString("66666666-6666-4666-8666-666666666648");
    private static final UUID WALLET_PUBLIC_ID = UUID.fromString("44444444-4444-4444-8444-444444444448");

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
        verify(walletRepository, never()).findByUserPublicIdForUpdate(any());
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
        verify(walletRepository, never()).findByUserPublicIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallyThrowsWhenRequiredInputIsMissing() {
        assertThatThrownBy(() -> service.payAutomatically(null, LocalDate.of(2026, 12, 31), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자동 원금 상환 대상 원장 id가 없습니다");
        assertThatThrownBy(() -> service.payAutomatically(1L, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자동 원금 상환 기준일이 없습니다");
        assertThatThrownBy(() -> service.payAutomatically(1L, LocalDate.of(2026, 12, 31), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자동 원금 상환 처리 시각이 없습니다");
    }

    @Test
    void payAutomaticallyThrowsWhenLedgerIsMissing() {
        when(principalRepaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payAutomatically(
                1L,
                LocalDate.of(2026, 12, 31),
                LocalDateTime.of(2026, 12, 31, 1, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자동 원금 상환 대상 원장을 찾을 수 없습니다");
    }

    @Test
    void payAutomaticallyThrowsWhenCreditLimitIsMissing() throws Exception {
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
        when(principalRepaymentCreditLimitRepository.findByPublicIdForUpdate(CREDIT_LIMIT_PUBLIC_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payAutomatically(
                1L,
                LocalDate.of(2026, 12, 31),
                LocalDateTime.of(2026, 12, 31, 1, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("원금 원장과 연결된 한도를 찾을 수 없습니다");

        verify(walletRepository, never()).findByUserPublicIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallyThrowsWhenWalletIsMissing() throws Exception {
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                LocalDate.of(2026, 12, 31),
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("10000.00"));

        when(principalRepaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ledger));
        when(principalRepaymentCreditLimitRepository.findByPublicIdForUpdate(creditLimit.getPublicId()))
                .thenReturn(Optional.of(creditLimit));
        when(walletRepository.findByUserPublicIdForUpdate(USER_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payAutomatically(
                1L,
                LocalDate.of(2026, 12, 31),
                LocalDateTime.of(2026, 12, 31, 1, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자동 원금 상환 대상 사용자의 지갑을 찾을 수 없습니다");

        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void creditLimitDecreaseUsedAmountRoundsMoneyAndRejectsInvalidAmount() throws Exception {
        CreditLimit creditLimit = creditLimit(10L, 1L, new BigDecimal("10000.129"));

        creditLimit.decreaseUsedAmount(new BigDecimal("1000.124"));

        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("9000.01");
        assertThat(creditLimit.hasUsedAmount()).isTrue();
        assertThatThrownBy(() -> creditLimit.decreaseUsedAmount(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("한도 사용액 감소 금액은 0보다 커야 합니다");
        assertThatThrownBy(() -> creditLimit.decreaseUsedAmount(new BigDecimal("9000.02")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("한도 사용액보다 큰 금액은 감소시킬 수 없습니다");
    }

    @Test
    void creditLimitHasUsedAmountReturnsFalseForNullOrZeroAmount() throws Exception {
        CreditLimit nullUsedAmount = creditLimit(10L, 1L, null);
        CreditLimit zeroUsedAmount = creditLimit(10L, 1L, BigDecimal.ZERO);

        assertThat(nullUsedAmount.hasUsedAmount()).isFalse();
        assertThat(zeroUsedAmount.hasUsedAmount()).isFalse();
    }

    @Test
    void principalOverdueLedgerResolveStoresResolvedAtAndRejectsNull() throws Exception {
        LoanOverdueLedger overdueLedger = overdueLedger(900L, 10L, 1L, new BigDecimal("5000.00"));
        LocalDateTime resolvedAt = LocalDateTime.of(2027, 1, 10, 1, 0);

        overdueLedger.resolve(resolvedAt);

        assertThat(overdueLedger.getResolvedAt()).isEqualTo(resolvedAt);
        assertThatThrownBy(() -> overdueLedger.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("연체 해소 시각은 null일 수 없습니다");
    }

    @Test
    void principalLedgerDomainMethodsCoverZeroAndInvalidBranches() throws Exception {
        PrincipalRepaymentLedger overpaidLedger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("12000.00"),
                PrincipalRepaymentLedger.STATUS_PARTIAL,
                LocalDate.of(2026, 12, 31),
                null
        );
        PrincipalRepaymentLedger cancelledLedger = principalLedger(
                2L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                "CANCELLED",
                LocalDate.of(2026, 12, 31),
                null
        );

        assertThat(overpaidLedger.getUnpaidAmount()).isEqualByComparingTo("0.00");
        assertThat(overpaidLedger.isPayableOn(LocalDate.of(2026, 12, 31))).isFalse();
        assertThat(cancelledLedger.isPayableOn(LocalDate.of(2026, 12, 31))).isFalse();
        assertThat(cancelledLedger.isOverdueDetectionTarget(LocalDate.of(2027, 1, 1))).isFalse();
        assertThatThrownBy(() -> cancelledLedger.markOverdue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("원금 연체 감지 시각이 없습니다");
        assertThatThrownBy(() -> cancelledLedger.applyPayment(BigDecimal.ZERO, LocalDate.of(2026, 12, 31), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("원금 자동 상환 금액은 0보다 커야 합니다");
    }

    @Test
    void principalLedgerApplyPaymentKeepsPastDuePartialAsOverdue() throws Exception {
        LocalDate dueDate = LocalDate.of(2026, 12, 31);
        PrincipalRepaymentLedger ledger = principalLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                PrincipalRepaymentLedger.STATUS_UPCOMING,
                dueDate,
                null
        );

        ledger.applyPayment(new BigDecimal("4000.00"), dueDate.plusDays(1), LocalDateTime.of(2027, 1, 1, 1, 0));

        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("4000.00");
        assertThat(ledger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_OVERDUE);
        assertThat(ledger.getPaidAt()).isNull();
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
        when(loanOverdueLedgerRepository.findAllByPrincipalRepaymentPublicIdAndResolvedAtIsNull(
                PRINCIPAL_REPAYMENT_PUBLIC_ID))
                .thenReturn(List.of(overdueLedger));

        Optional<PrincipalRepaymentLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(ledger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_PAID);
        assertThat(overdueLedger.getResolvedAt()).isEqualTo(now);
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
        verify(loanOverdueLedgerRepository, never()).findAllByPrincipalRepaymentPublicIdAndResolvedAtIsNull(any());
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
        when(principalRepaymentCreditLimitRepository.findByPublicIdForUpdate(creditLimit.getPublicId()))
                .thenReturn(Optional.of(creditLimit));
        when(walletRepository.findByUserPublicIdForUpdate(creditLimit.getUserPublicId())).thenReturn(Optional.of(wallet));
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
        setField(ledger, "publicId", PRINCIPAL_REPAYMENT_PUBLIC_ID);
        setField(ledger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(ledger, "orderPublicId", ORDER_PUBLIC_ID);
        setField(ledger, "paymentRequestPublicId", PAYMENT_REQUEST_PUBLIC_ID);
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
        setField(creditLimit, "publicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(creditLimit, "userPublicId", USER_PUBLIC_ID);
        setField(creditLimit, "usedAmount", usedAmount);
        setField(creditLimit, "status", "ACTIVE");
        return creditLimit;
    }

    private Wallet wallet(Long id, Long userId, BigDecimal balance) throws Exception {
        Wallet wallet = newInstance(Wallet.class);
        setField(wallet, "id", id);
        setField(wallet, "publicId", WALLET_PUBLIC_ID);
        setField(wallet, "userPublicId", USER_PUBLIC_ID);
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
        setField(overdueLedger, "publicId", UUID.fromString("99999999-9999-4999-8999-999999999948"));
        setField(overdueLedger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(overdueLedger, "principalRepaymentPublicId", PRINCIPAL_REPAYMENT_PUBLIC_ID);
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
