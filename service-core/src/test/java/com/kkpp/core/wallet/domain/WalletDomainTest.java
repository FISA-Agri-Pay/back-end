package com.kkpp.core.wallet.domain;

import static com.kkpp.core.testsupport.TestEntityFactory.creditLimit;
import static com.kkpp.core.testsupport.TestEntityFactory.interestLedger;
import static com.kkpp.core.testsupport.TestEntityFactory.principalLedger;
import static com.kkpp.core.testsupport.TestEntityFactory.wallet;
import static com.kkpp.core.testsupport.TestEntityFactory.walletTransaction;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletDomainTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID WALLET_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void interestLedgerUnpaidAmountNeverGoesBelowZero() {
        InterestLedger partial = interestLedger(
                CREDIT_LIMIT_PUBLIC_ID,
                LocalDate.now(),
                new BigDecimal("10000"),
                new BigDecimal("3000"),
                InterestLedger.STATUS_PARTIAL
        );
        InterestLedger overPaid = interestLedger(
                CREDIT_LIMIT_PUBLIC_ID,
                LocalDate.now(),
                new BigDecimal("10000"),
                new BigDecimal("15000"),
                InterestLedger.STATUS_PARTIAL
        );

        assertThat(partial.getUnpaidAmount()).isEqualByComparingTo("7000");
        assertThat(overPaid.getUnpaidAmount()).isEqualByComparingTo("0");
    }

    @Test
    void readOnlyWalletEntitiesExposeMappedFields() {
        Wallet wallet = wallet(WALLET_PUBLIC_ID, USER_PUBLIC_ID, new BigDecimal("500000"));
        CreditLimit creditLimit = creditLimit(
                CREDIT_LIMIT_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("3000000"),
                new BigDecimal("700000"),
                LocalDate.of(2026, 12, 11)
        );
        PrincipalRepaymentLedger principalLedger = principalLedger(
                CREDIT_LIMIT_PUBLIC_ID,
                LocalDate.of(2026, 12, 11),
                PrincipalRepaymentLedger.STATUS_UPCOMING
        );
        WalletTransaction transaction = walletTransaction(
                WALLET_PUBLIC_ID,
                WalletTransaction.TYPE_PRINCIPAL_PAYMENT,
                new BigDecimal("70000"),
                LocalDateTime.of(2026, 5, 12, 10, 0)
        );

        assertThat(wallet.getPublicId()).isEqualTo(WALLET_PUBLIC_ID);
        assertThat(wallet.getBalance()).isEqualByComparingTo("500000");
        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("700000");
        assertThat(principalLedger.getStatus()).isEqualTo(PrincipalRepaymentLedger.STATUS_UPCOMING);
        assertThat(transaction.getTransactionType()).isEqualTo(WalletTransaction.TYPE_PRINCIPAL_PAYMENT);
    }
}
