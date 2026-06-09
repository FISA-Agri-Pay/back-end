package com.kkpp.core.wallet.service;

import static com.kkpp.core.testsupport.TestEntityFactory.application;
import static com.kkpp.core.testsupport.TestEntityFactory.creditLimit;
import static com.kkpp.core.testsupport.TestEntityFactory.interestLedger;
import static com.kkpp.core.testsupport.TestEntityFactory.principalLedger;
import static com.kkpp.core.testsupport.TestEntityFactory.set;
import static com.kkpp.core.testsupport.TestEntityFactory.user;
import static com.kkpp.core.testsupport.TestEntityFactory.wallet;
import static com.kkpp.core.testsupport.TestEntityFactory.walletTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import com.kkpp.core.user.repository.UserRepository;
import com.kkpp.core.wallet.domain.CreditLimit;
import com.kkpp.core.wallet.domain.InterestLedger;
import com.kkpp.core.wallet.domain.PrincipalRepaymentLedger;
import com.kkpp.core.wallet.domain.WalletTransaction;
import com.kkpp.core.wallet.dto.WalletCreditSummaryResponse;
import com.kkpp.core.wallet.dto.WalletMeResponse;
import com.kkpp.core.wallet.exception.WalletErrorCode;
import com.kkpp.core.wallet.exception.WalletException;
import com.kkpp.core.wallet.repository.CreditLimitRepository;
import com.kkpp.core.wallet.repository.InterestLedgerRepository;
import com.kkpp.core.wallet.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.core.wallet.repository.WalletRepository;
import com.kkpp.core.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID WALLET_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private CreditLimitRepository creditLimitRepository;

    @Mock
    private CreditLimitApplicationRepository creditLimitApplicationRepository;

    @Mock
    private InterestLedgerRepository interestLedgerRepository;

    @Mock
    private PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private WalletQueryService walletQueryService;

    @BeforeEach
    void setUp() {
        walletQueryService = new WalletQueryService(
                userRepository,
                walletRepository,
                creditLimitRepository,
                creditLimitApplicationRepository,
                interestLedgerRepository,
                principalRepaymentLedgerRepository,
                walletTransactionRepository
        );
    }

    @Test
    void getMyCreditSummaryReturnsActiveLimitAndCapsUsageRateAtOneHundred() {
        CreditLimit limit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("1000000"),
                new BigDecimal("1200000"),
                LocalDate.now().plusMonths(2)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(limit));

        WalletCreditSummaryResponse response = walletQueryService.getMyCreditSummary(USER_ID);

        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.hasActiveLimit()).isTrue();
        assertThat(response.remainingAmount()).isEqualByComparingTo("0");
        assertThat(response.usageRate()).isEqualByComparingTo("100.0");
        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.APPROVED.name());
    }

    @Test
    void getMyCreditSummaryReturnsPositiveRemainingAmountAndUsageRate() {
        CreditLimit limit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("2000000"),
                new BigDecimal("500000"),
                LocalDate.now().plusMonths(2)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(limit));

        WalletCreditSummaryResponse response = walletQueryService.getMyCreditSummary(USER_ID);

        assertThat(response.remainingAmount()).isEqualByComparingTo("1500000");
        assertThat(response.usageRate()).isEqualByComparingTo("25.0");
    }

    @Test
    void getMyCreditSummaryReturnsZeroUsageRateWhenTotalLimitIsNullOrZero() {
        CreditLimit nullTotalLimit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                null,
                null,
                LocalDate.now().plusMonths(2)
        );
        CreditLimit zeroTotalLimit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                BigDecimal.ZERO,
                new BigDecimal("500000"),
                LocalDate.now().plusMonths(2)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(nullTotalLimit))
                .thenReturn(Optional.of(zeroTotalLimit));

        WalletCreditSummaryResponse nullTotalResponse = walletQueryService.getMyCreditSummary(USER_ID);
        WalletCreditSummaryResponse zeroTotalResponse = walletQueryService.getMyCreditSummary(USER_ID);

        assertThat(nullTotalResponse.usageRate()).isEqualByComparingTo("0.0");
        assertThat(zeroTotalResponse.usageRate()).isEqualByComparingTo("0.0");
    }

    @Test
    void getMyCreditSummaryReturnsZeroUsageRateWhenTotalLimitIsNegative() {
        CreditLimit negativeTotalLimit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("-1"),
                new BigDecimal("500000"),
                LocalDate.now().plusMonths(2)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(negativeTotalLimit));

        WalletCreditSummaryResponse response = walletQueryService.getMyCreditSummary(USER_ID);

        assertThat(response.usageRate()).isEqualByComparingTo("0.0");
    }


    @Test
    void getMyCreditSummaryReturnsLatestApplicationStatusWhenNoActiveLimitExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(creditLimitApplicationRepository.findTopByUserPublicIdOrderByAppliedAtDesc(USER_PUBLIC_ID))
                .thenReturn(Optional.of(application(USER_PUBLIC_ID, ApplicationStatus.PENDING)));

        WalletCreditSummaryResponse response = walletQueryService.getMyCreditSummary(USER_ID);

        assertThat(response.hasActiveLimit()).isFalse();
        assertThat(response.totalLimit()).isEqualByComparingTo("0");
        assertThat(response.usageRate()).isEqualByComparingTo("0.0");
        assertThat(response.applicationStatus()).isEqualTo(ApplicationStatus.PENDING.name());
    }

    @Test
    void getMyCreditSummaryReturnsNullApplicationStatusWhenNoApplicationExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(creditLimitApplicationRepository.findTopByUserPublicIdOrderByAppliedAtDesc(USER_PUBLIC_ID))
                .thenReturn(Optional.empty());

        WalletCreditSummaryResponse response = walletQueryService.getMyCreditSummary(USER_ID);

        assertThat(response.hasActiveLimit()).isFalse();
        assertThat(response.applicationStatus()).isNull();
    }

    @Test
    void getMyWalletThrowsWhenWalletDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletQueryService.getMyWallet(USER_ID))
                .isInstanceOf(WalletException.class)
                .extracting("errorCode")
                .isEqualTo(WalletErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    void getMyWalletReturnsNearestRepaymentDateAndNegativeTransactionAmount() {
        LocalDate interestDueDate = LocalDate.now().plusDays(3);
        LocalDate principalDueDate = LocalDate.now().plusDays(10);
        CreditLimit limit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("3000000"),
                new BigDecimal("700000"),
                principalDueDate.plusMonths(1)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(wallet(WALLET_PUBLIC_ID, USER_PUBLIC_ID, new BigDecimal("500000"))));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(limit));
        when(interestLedgerRepository.findNearestUnpaidLedger(eq(CREDIT_LIMIT_PUBLIC_ID), anyCollection()))
                .thenReturn(Optional.of(interestLedger(
                        CREDIT_LIMIT_PUBLIC_ID,
                        interestDueDate,
                        new BigDecimal("10000"),
                        new BigDecimal("3000"),
                        InterestLedger.STATUS_PARTIAL
                )));
        when(principalRepaymentLedgerRepository.findNearestUnpaidLedger(eq(CREDIT_LIMIT_PUBLIC_ID), anyCollection()))
                .thenReturn(Optional.of(principalLedger(
                        CREDIT_LIMIT_PUBLIC_ID,
                        principalDueDate,
                        PrincipalRepaymentLedger.STATUS_UPCOMING
                )));
        when(walletTransactionRepository.findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(eq(WALLET_PUBLIC_ID), anyCollection()))
                .thenReturn(List.of(walletTransaction(
                        WALLET_PUBLIC_ID,
                        WalletTransaction.TYPE_INTEREST_PAYMENT,
                        new BigDecimal("10000"),
                        LocalDateTime.of(2026, 5, 11, 10, 0)
                )));

        WalletMeResponse response = walletQueryService.getMyWallet(USER_ID);

        assertThat(response.walletPublicId()).isEqualTo(WALLET_PUBLIC_ID);
        assertThat(response.nextRepaymentDate()).isEqualTo(interestDueDate);
        assertThat(response.monthlyInterest().amount()).isEqualByComparingTo("7000");
        assertThat(response.principal().remainingAmount()).isEqualByComparingTo("700000");
        assertThat(response.transactions()).hasSize(1);
        assertThat(response.transactions().getFirst().title()).isEqualTo("4월 이자 상환");
        assertThat(response.transactions().getFirst().amount()).isEqualByComparingTo("-10000");
    }

    @Test
    void getMyWalletReturnsEmptyCreditAreaWhenNoActiveLimitExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(wallet(WALLET_PUBLIC_ID, USER_PUBLIC_ID, new BigDecimal("500000"))));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(walletTransactionRepository.findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(eq(WALLET_PUBLIC_ID), anyCollection()))
                .thenReturn(List.of());

        WalletMeResponse response = walletQueryService.getMyWallet(USER_ID);

        assertThat(response.nextRepaymentDate()).isNull();
        assertThat(response.monthlyInterest()).isNull();
        assertThat(response.principal()).isNull();
        assertThat(response.transactions()).isEmpty();
    }

    @Test
    void getMyWalletUsesPrincipalFallbackStatusAndTransactionFallbackTitles() {
        CreditLimit usedLimit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("3000000"),
                new BigDecimal("700000"),
                LocalDate.of(2026, 12, 11)
        );
        WalletTransaction principalPayment = walletTransaction(
                WALLET_PUBLIC_ID,
                WalletTransaction.TYPE_PRINCIPAL_PAYMENT,
                new BigDecimal("-70000"),
                LocalDateTime.of(2026, 5, 12, 10, 0)
        );
        WalletTransaction deposit = walletTransaction(
                WALLET_PUBLIC_ID,
                WalletTransaction.TYPE_DEPOSIT,
                new BigDecimal("100000"),
                LocalDateTime.of(2026, 5, 13, 10, 0)
        );
        WalletTransaction custom = walletTransaction(
                WALLET_PUBLIC_ID,
                "ADJUSTMENT",
                new BigDecimal("5000"),
                LocalDateTime.of(2026, 5, 14, 10, 0)
        );
        WalletTransaction customWithoutDescription = walletTransaction(
                WALLET_PUBLIC_ID,
                "MANUAL",
                new BigDecimal("3000"),
                LocalDateTime.of(2026, 5, 15, 10, 0)
        );
        WalletTransaction interestWithoutDateAndAmount = walletTransaction(
                WALLET_PUBLIC_ID,
                WalletTransaction.TYPE_INTEREST_PAYMENT,
                null,
                null
        );
        set(custom, "description", "정산 조정");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(wallet(WALLET_PUBLIC_ID, USER_PUBLIC_ID, new BigDecimal("500000"))));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(usedLimit));
        when(interestLedgerRepository.findNearestUnpaidLedger(eq(CREDIT_LIMIT_PUBLIC_ID), anyCollection()))
                .thenReturn(Optional.empty());
        when(principalRepaymentLedgerRepository.findNearestUnpaidLedger(eq(CREDIT_LIMIT_PUBLIC_ID), anyCollection()))
                .thenReturn(Optional.empty());
        when(walletTransactionRepository.findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(eq(WALLET_PUBLIC_ID), anyCollection()))
                .thenReturn(List.of(principalPayment, deposit, custom, customWithoutDescription, interestWithoutDateAndAmount));

        WalletMeResponse response = walletQueryService.getMyWallet(USER_ID);

        assertThat(response.principal().dueDate()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(response.principal().status()).isEqualTo(PrincipalRepaymentLedger.STATUS_UPCOMING);
        assertThat(response.transactions()).extracting(WalletMeResponse.Transaction::title)
                .containsExactly("원금 상환", "지갑 입금", "정산 조정", "MANUAL", "이자 상환");
        assertThat(response.transactions().get(0).amount()).isEqualByComparingTo("-70000");
        assertThat(response.transactions().get(1).amount()).isEqualByComparingTo("100000");
        assertThat(response.transactions().get(2).amount()).isEqualByComparingTo("5000");
        assertThat(response.transactions().get(3).amount()).isEqualByComparingTo("3000");
        assertThat(response.transactions().get(4).amount()).isNull();
    }

    @Test
    void getMyWalletReturnsNullPrincipalStatusWhenUsedAmountIsZero() {
        CreditLimit unusedLimit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("3000000"),
                BigDecimal.ZERO,
                LocalDate.of(2026, 12, 11)
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(wallet(WALLET_PUBLIC_ID, USER_PUBLIC_ID, new BigDecimal("500000"))));
        when(creditLimitRepository.findLatestUsableActiveLimit(eq(USER_PUBLIC_ID), eq(CreditLimit.STATUS_ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(unusedLimit));
        when(interestLedgerRepository.findNearestUnpaidLedger(eq(CREDIT_LIMIT_PUBLIC_ID), anyCollection()))
                .thenReturn(Optional.empty());
        when(principalRepaymentLedgerRepository.findNearestUnpaidLedger(eq(CREDIT_LIMIT_PUBLIC_ID), anyCollection()))
                .thenReturn(Optional.empty());
        when(walletTransactionRepository.findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(eq(WALLET_PUBLIC_ID), anyCollection()))
                .thenReturn(List.of());

        WalletMeResponse response = walletQueryService.getMyWallet(USER_ID);

        assertThat(response.principal().status()).isNull();
    }
}
