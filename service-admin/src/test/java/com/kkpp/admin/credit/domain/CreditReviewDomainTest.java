package com.kkpp.admin.credit.domain;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.creditReviewApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreditReviewDomainTest {

    private static final UUID APPLICATION_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID USER_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID REVIEWER_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void approveChangesPendingApplicationToApproved() {
        CreditReviewApplication application = creditReviewApplication(1L, APPLICATION_PUBLIC_ID, USER_PUBLIC_ID, CreditReviewStatus.PENDING);
        LocalDateTime decidedAt = LocalDateTime.of(2026, 6, 13, 10, 0);

        application.approve(REVIEWER_PUBLIC_ID, new BigDecimal("1000000"), decidedAt);

        assertThat(application.getStatus()).isEqualTo(CreditReviewStatus.APPROVED);
        assertThat(application.getApprovedAmount()).isEqualByComparingTo("1000000");
        assertThat(application.getReviewedByAdminPublicId()).isEqualTo(REVIEWER_PUBLIC_ID);
        assertThat(application.getDecidedAt()).isEqualTo(decidedAt);
        assertThat(application.getRejectionReason()).isNull();
    }

    @Test
    void approveRejectsInvalidStateAndAmount() {
        CreditReviewApplication approved = creditReviewApplication(1L, APPLICATION_PUBLIC_ID, USER_PUBLIC_ID, CreditReviewStatus.APPROVED);
        CreditReviewApplication pending = creditReviewApplication(1L, APPLICATION_PUBLIC_ID, USER_PUBLIC_ID, CreditReviewStatus.PENDING);

        assertThatThrownBy(() -> approved.approve(REVIEWER_PUBLIC_ID, BigDecimal.ONE, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.approve(null, BigDecimal.ONE, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.approve(REVIEWER_PUBLIC_ID, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.approve(REVIEWER_PUBLIC_ID, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.approve(REVIEWER_PUBLIC_ID, BigDecimal.ZERO, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectChangesPendingApplicationToRejected() {
        CreditReviewApplication application = creditReviewApplication(1L, APPLICATION_PUBLIC_ID, USER_PUBLIC_ID, CreditReviewStatus.PENDING);
        LocalDateTime decidedAt = LocalDateTime.of(2026, 6, 13, 10, 0);

        application.reject(REVIEWER_PUBLIC_ID, "서류 미비", decidedAt);

        assertThat(application.getStatus()).isEqualTo(CreditReviewStatus.REJECTED);
        assertThat(application.getApprovedAmount()).isNull();
        assertThat(application.getRejectionReason()).isEqualTo("서류 미비");
        assertThat(application.getDecidedAt()).isEqualTo(decidedAt);
    }

    @Test
    void rejectRejectsInvalidStateAndRequiredValues() {
        CreditReviewApplication rejected = creditReviewApplication(1L, APPLICATION_PUBLIC_ID, USER_PUBLIC_ID, CreditReviewStatus.REJECTED);
        CreditReviewApplication pending = creditReviewApplication(1L, APPLICATION_PUBLIC_ID, USER_PUBLIC_ID, CreditReviewStatus.PENDING);

        assertThatThrownBy(() -> rejected.reject(REVIEWER_PUBLIC_ID, "서류 미비", LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.reject(null, "서류 미비", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.reject(REVIEWER_PUBLIC_ID, "서류 미비", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.reject(REVIEWER_PUBLIC_ID, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.reject(REVIEWER_PUBLIC_ID, " ", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issueLimitCreatesActiveLimitFromApplication() {
        CreditReviewApplication application = creditReviewApplication(1L, APPLICATION_PUBLIC_ID, USER_PUBLIC_ID, CreditReviewStatus.APPROVED);

        CreditReviewLimit limit = CreditReviewLimit.issue(
                application,
                new BigDecimal("1000000"),
                new BigDecimal("0.0450"),
                "RICE",
                20,
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 6, 13)
        );

        assertThat(limit.getPublicId()).isNotNull();
        assertThat(limit.getUser()).isEqualTo(application.getUser());
        assertThat(limit.getApplication()).isEqualTo(application);
        assertThat(limit.getUsedAmount()).isEqualByComparingTo("0");
        assertThat(limit.getStatus()).isEqualTo(CreditLimitStatus.ACTIVE);
    }

    @Test
    void issueWalletCreatesDefaultActiveWallet() {
        CreditReviewWallet wallet = CreditReviewWallet.issue(USER_PUBLIC_ID);

        assertThat(wallet.getPublicId()).isNotNull();
        assertThat(wallet.getUserPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(wallet.getBalance()).isEqualByComparingTo("0");
        assertThat(wallet.getDepositBankName()).isEqualTo("local-bank");
        assertThat(wallet.getDepositAccountNumber()).isEqualTo("KKPP-" + USER_PUBLIC_ID.toString().replace("-", ""));
        assertThat(wallet.getStatus()).isEqualTo(CreditReviewWallet.STATUS_ACTIVE);

        assertThatThrownBy(() -> CreditReviewWallet.issue(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
