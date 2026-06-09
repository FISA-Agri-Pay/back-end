package com.kkpp.batch.interest.payment.service;

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

    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111148");
    private static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333348");
    private static final UUID APPLICATION_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222248");
    private static final UUID INTEREST_LEDGER_PUBLIC_ID = UUID.fromString("77777777-7777-4777-8777-777777777748");
    private static final UUID WALLET_PUBLIC_ID = UUID.fromString("44444444-4444-4444-8444-444444444448");

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
    void payAutomaticallyFailsWhenWalletIsNotActive() throws Exception {
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
        Wallet wallet = wallet(100L, 1L, new BigDecimal("30000.00"), "SUSPENDED");

        mockLedgerContext(ledger, creditLimit(10L, 1L), wallet);

        assertThatThrownBy(() -> service.payAutomatically(1L, today, LocalDateTime.of(2026, 5, 11, 1, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("활성 상태 지갑만 자동 이자 상환에 사용할 수 있습니다.");

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
        verify(walletRepository, never()).findByUserPublicIdForUpdate(any());
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
        verify(walletRepository, never()).findByUserPublicIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallyThrowsWhenRequiredInputIsMissing() {
        assertThatThrownBy(() -> service.payAutomatically(null, LocalDate.of(2026, 5, 11), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자동 이자 상환 대상 원장 id가 없습니다");
        assertThatThrownBy(() -> service.payAutomatically(1L, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자동 이자 상환 기준일이 없습니다");
        assertThatThrownBy(() -> service.payAutomatically(1L, LocalDate.of(2026, 5, 11), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자동 이자 상환 처리 시각이 없습니다");
    }

    @Test
    void payAutomaticallyThrowsWhenLedgerIsMissing() {
        when(interestPaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payAutomatically(
                1L,
                LocalDate.of(2026, 5, 11),
                LocalDateTime.of(2026, 5, 11, 1, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자동 이자 상환 대상 원장을 찾을 수 없습니다");
    }

    @Test
    void payAutomaticallyThrowsWhenCreditLimitIsMissing() throws Exception {
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
        when(interestPaymentCreditLimitRepository.findByPublicId(CREDIT_LIMIT_PUBLIC_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payAutomatically(
                1L,
                LocalDate.of(2026, 5, 11),
                LocalDateTime.of(2026, 5, 11, 1, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이자 원장과 연결된 한도를 찾을 수 없습니다");

        verify(walletRepository, never()).findByUserPublicIdForUpdate(any());
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void payAutomaticallyThrowsWhenWalletIsMissing() throws Exception {
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_UPCOMING,
                LocalDate.of(2026, 5, 11),
                null
        );
        CreditLimit creditLimit = creditLimit(10L, 1L);

        when(interestPaymentLedgerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ledger));
        when(interestPaymentCreditLimitRepository.findByPublicId(creditLimit.getPublicId()))
                .thenReturn(Optional.of(creditLimit));
        when(walletRepository.findByUserPublicIdForUpdate(USER_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payAutomatically(
                1L,
                LocalDate.of(2026, 5, 11),
                LocalDateTime.of(2026, 5, 11, 1, 0)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("자동 이자 상환 대상 사용자의 지갑을 찾을 수 없습니다");

        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void walletWithdrawRoundsMoneyAndRejectsInvalidAmount() throws Exception {
        Wallet wallet = wallet(100L, 1L, new BigDecimal("10000.129"));

        wallet.withdraw(new BigDecimal("1000.124"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("9000.01");
        assertThatThrownBy(() -> wallet.withdraw(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지갑 차감 금액은 0보다 커야 합니다");
        assertThatThrownBy(() -> wallet.withdraw(new BigDecimal("9000.02")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지갑 잔액보다 큰 금액은 차감할 수 없습니다");
    }

    @Test
    void walletTransactionFactoriesPopulatePaymentMetadata() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 1, 0);

        WalletTransaction interestTransaction = WalletTransaction.interestPayment(
                WALLET_PUBLIC_ID,
                new BigDecimal("10000.00"),
                new BigDecimal("20000.00"),
                INTEREST_LEDGER_PUBLIC_ID,
                now
        );
        WalletTransaction principalTransaction = WalletTransaction.principalPayment(
                WALLET_PUBLIC_ID,
                new BigDecimal("30000.00"),
                new BigDecimal("0.00"),
                UUID.fromString("99999999-9999-4999-8999-999999999948"),
                now
        );

        assertThat(interestTransaction.getTransactionType()).isEqualTo(WalletTransaction.TYPE_INTEREST_PAYMENT);
        assertThat(interestTransaction.getRelatedType()).isEqualTo("INTEREST_LEDGER");
        assertThat(interestTransaction.getDescription()).isEqualTo("이자 자동 상환");
        assertThat(interestTransaction.getTransactedAt()).isEqualTo(now);
        assertThat(principalTransaction.getTransactionType()).isEqualTo(WalletTransaction.TYPE_PRINCIPAL_PAYMENT);
        assertThat(principalTransaction.getRelatedType()).isEqualTo("PRINCIPAL_REPAYMENT_LEDGER");
        assertThat(principalTransaction.getDescription()).isEqualTo("원금 자동 상환");
        assertThat(principalTransaction.getTransactedAt()).isEqualTo(now);
    }

    @Test
    void interestOverdueLedgerResolveStoresResolvedAtAndRejectsNull() throws Exception {
        LoanOverdueLedger overdueLedger = overdueLedger(900L, 10L, 1L, new BigDecimal("5000.00"));
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 5, 20, 1, 0);

        assertThat(overdueLedger.isUnresolved()).isTrue();

        overdueLedger.resolve(resolvedAt);

        assertThat(overdueLedger.isUnresolved()).isFalse();
        assertThat(overdueLedger.getResolvedAt()).isEqualTo(resolvedAt);
        assertThatThrownBy(() -> overdueLedger.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("연체 해소 시각은 null일 수 없습니다");
    }

    @Test
    void interestLedgerDomainMethodsCoverZeroAndInvalidBranches() throws Exception {
        InterestLedger overpaidLedger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                new BigDecimal("12000.00"),
                InterestLedger.STATUS_PARTIAL,
                LocalDate.of(2026, 5, 11),
                null
        );
        InterestLedger cancelledLedger = interestLedger(
                2L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                "CANCELLED",
                LocalDate.of(2026, 5, 11),
                null
        );

        assertThat(overpaidLedger.getUnpaidAmount()).isEqualByComparingTo("0.00");
        assertThat(overpaidLedger.isPayableOn(LocalDate.of(2026, 5, 11))).isFalse();
        assertThat(cancelledLedger.isPayableOn(LocalDate.of(2026, 5, 11))).isFalse();
        assertThat(cancelledLedger.isOverdueDetectionTarget(LocalDate.of(2026, 5, 12))).isFalse();
        assertThatThrownBy(() -> cancelledLedger.markOverdue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이자 연체 감지 시각이 없습니다");
        assertThatThrownBy(() -> cancelledLedger.applyPayment(BigDecimal.ZERO, LocalDate.of(2026, 5, 11), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이자 자동 상환 금액은 0보다 커야 합니다");
    }

    @Test
    void interestLedgerApplyPaymentKeepsPastDuePartialAsOverdue() throws Exception {
        LocalDate dueDate = LocalDate.of(2026, 5, 11);
        LocalDateTime paidAt = LocalDateTime.of(2026, 5, 20, 1, 0);
        InterestLedger ledger = interestLedger(
                1L,
                10L,
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                InterestLedger.STATUS_UPCOMING,
                dueDate,
                null
        );

        ledger.applyPayment(new BigDecimal("4000.00"), dueDate.plusDays(1), paidAt);

        assertThat(ledger.getAmountPaid()).isEqualByComparingTo("4000.00");
        assertThat(ledger.getStatus()).isEqualTo(InterestLedger.STATUS_OVERDUE);
        assertThat(ledger.getPaidAt()).isNull();
        assertThat(ledger.getUpdatedAt()).isEqualTo(paidAt);
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
        when(loanOverdueLedgerRepository.findAllByInterestLedgerPublicIdAndResolvedAtIsNull(INTEREST_LEDGER_PUBLIC_ID))
                .thenReturn(List.of(overdueLedger));

        Optional<InterestLedger> result = service.payAutomatically(1L, today, now);

        assertThat(result).contains(ledger);
        assertThat(ledger.getStatus()).isEqualTo(InterestLedger.STATUS_PAID);
        assertThat(overdueLedger.getResolvedAt()).isEqualTo(now);
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
        when(interestPaymentCreditLimitRepository.findByPublicId(creditLimit.getPublicId()))
                .thenReturn(Optional.of(creditLimit));
        when(walletRepository.findByUserPublicIdForUpdate(creditLimit.getUserPublicId())).thenReturn(Optional.of(wallet));
    }

    private CreditLimit creditLimit(Long id, Long userId) throws Exception {
        CreditLimit creditLimit = newInstance(CreditLimit.class);
        setField(creditLimit, "id", id);
        setField(creditLimit, "publicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(creditLimit, "userPublicId", USER_PUBLIC_ID);
        setField(creditLimit, "applicationPublicId", APPLICATION_PUBLIC_ID);
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
        setField(ledger, "publicId", INTEREST_LEDGER_PUBLIC_ID);
        setField(ledger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
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
        return wallet(id, userId, balance, "ACTIVE");
    }

    private Wallet wallet(Long id, Long userId, BigDecimal balance, String status) throws Exception {
        Wallet wallet = newInstance(Wallet.class);
        setField(wallet, "id", id);
        setField(wallet, "publicId", WALLET_PUBLIC_ID);
        setField(wallet, "userPublicId", USER_PUBLIC_ID);
        setField(wallet, "balance", balance);
        setField(wallet, "status", status);
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
        setField(overdueLedger, "publicId", UUID.fromString("88888888-8888-4888-8888-888888888848"));
        setField(overdueLedger, "creditLimitPublicId", CREDIT_LIMIT_PUBLIC_ID);
        setField(overdueLedger, "interestLedgerPublicId", INTEREST_LEDGER_PUBLIC_ID);
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
