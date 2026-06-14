package com.kkpp.catalog.paymentpin.domain;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.EVENT_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PAYMENT_REQUEST_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.VERIFICATION_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.paymentPinVerifiedEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kkpp.catalog.paymentpin.event.PaymentPinVerifiedEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PaymentPinVerificationDomainTest {

    @Test
    void createFromVerifiedEventAndMarkUsed() {
        PaymentPinVerification verification = PaymentPinVerification.from(
                paymentPinVerifiedEvent(EVENT_ID, VERIFICATION_ID, USER_PUBLIC_ID)
        );

        assertThat(verification.isOwnedBy(USER_PUBLIC_ID)).isTrue();
        assertThat(verification.isVerified()).isTrue();
        assertThat(verification.isExpired(LocalDateTime.of(2026, 6, 14, 0, 1))).isFalse();

        LocalDateTime usedAt = LocalDateTime.of(2026, 6, 14, 0, 2);
        verification.markUsed(PAYMENT_REQUEST_PUBLIC_ID, usedAt);

        assertThat(verification.isVerified()).isFalse();
        assertThat(verification.getStatus()).isEqualTo(PaymentPinVerification.STATUS_USED);
        assertThat(verification.getPaymentRequestPublicId()).isEqualTo(PAYMENT_REQUEST_PUBLIC_ID);
        assertThat(verification.getUsedAt()).isEqualTo(usedAt);
    }

    @Test
    void rejectInvalidEventAndMarkUsedArguments() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");

        assertThatThrownBy(() -> PaymentPinVerification.from(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PaymentPinVerification.from(new PaymentPinVerifiedEvent(
                EVENT_ID,
                VERIFICATION_ID,
                USER_PUBLIC_ID,
                now,
                now.plusSeconds(300),
                "LOGIN_PIN"
        ))).isInstanceOf(IllegalArgumentException.class);

        PaymentPinVerification verification = PaymentPinVerification.from(
                paymentPinVerifiedEvent(EVENT_ID, VERIFICATION_ID, USER_PUBLIC_ID)
        );
        assertThatThrownBy(() -> verification.markUsed(null, LocalDateTime.now(ZoneOffset.UTC)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> verification.markUsed(PAYMENT_REQUEST_PUBLIC_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
