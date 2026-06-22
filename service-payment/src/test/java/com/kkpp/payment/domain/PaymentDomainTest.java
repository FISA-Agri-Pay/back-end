package com.kkpp.payment.domain;

import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.CREDIT_LIMIT_PUBLIC_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.EVENT_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.ORDER_PUBLIC_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.PAYMENT_REQUEST_PUBLIC_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.activeCreditLimit;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.deliveryAddress;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.item;
import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentDomainTest {

    @Test
    void orderConfirmedCopiesPaymentRequestAndItems() {
        LocalDateTime orderedAt = LocalDateTime.of(2026, 6, 14, 10, 30);

        Order order = Order.confirmed(
                ORDER_PUBLIC_ID,
                USER_PUBLIC_ID,
                PAYMENT_REQUEST_PUBLIC_ID,
                new BigDecimal("120000"),
                deliveryAddress(),
                List.of(item()),
                orderedAt
        );

        assertThat(order.getPublicId()).isEqualTo(ORDER_PUBLIC_ID);
        assertThat(order.getUserPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(order.getPaymentRequestPublicId()).isEqualTo(PAYMENT_REQUEST_PUBLIC_ID);
        assertThat(order.getOrderStatus()).isEqualTo("CONFIRMED");
        assertThat(order.getDeliveryStatus()).isEqualTo("PREPARING");
        assertThat(order.getRecipientName()).isEqualTo("홍길동");
        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getOrderItems().getFirst().getOrder()).isEqualTo(order);
        assertThat(order.getOrderItems().getFirst().getTotalPrice()).isEqualByComparingTo("120000");
    }

    @Test
    void orderConfirmedRejectsMissingRequiredValues() {
        CreditPaymentRequestedMessage message = message();

        assertThatThrownBy(() -> Order.confirmed(null, USER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ONE, deliveryAddress(), message.items(), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.confirmed(ORDER_PUBLIC_ID, null, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ONE, deliveryAddress(), message.items(), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.confirmed(ORDER_PUBLIC_ID, USER_PUBLIC_ID, null,
                BigDecimal.ONE, deliveryAddress(), message.items(), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.confirmed(ORDER_PUBLIC_ID, USER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                null, deliveryAddress(), message.items(), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.confirmed(ORDER_PUBLIC_ID, USER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ONE, null, message.items(), LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.confirmed(ORDER_PUBLIC_ID, USER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ONE, deliveryAddress(), message.items(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creditLimitCalculatesAvailabilityAndUsesAmount() {
        CreditLimit creditLimit = activeCreditLimit();

        assertThat(creditLimit.isActive(LocalDate.now())).isTrue();
        assertThat(creditLimit.availableAmount()).isEqualByComparingTo("500000");
        assertThat(creditLimit.canUse(new BigDecimal("120000"))).isTrue();

        creditLimit.use(new BigDecimal("120000"));

        assertThat(creditLimit.getUsedAmount()).isEqualByComparingTo("120000");
        assertThat(creditLimit.availableAmount()).isEqualByComparingTo("380000");
    }

    @Test
    void creditLimitRejectsInactiveExpiredAndInvalidAmount() {
        assertThat(activeCreditLimit().canUse(new BigDecimal("999999"))).isFalse();
        assertThat(activeCreditLimit().isActive(LocalDate.now().plusYears(1))).isFalse();
        assertThatThrownBy(() -> activeCreditLimit().canUse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activeCreditLimit().use(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> activeCreditLimit().use(new BigDecimal("999999")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creditUsageLedgerPurchaseCreatesUsageLedgerAndValidatesInputs() {
        LocalDateTime usedAt = LocalDateTime.of(2026, 6, 14, 10, 30);

        CreditUsageLedger ledger = CreditUsageLedger.purchase(
                CREDIT_LIMIT_PUBLIC_ID,
                ORDER_PUBLIC_ID,
                PAYMENT_REQUEST_PUBLIC_ID,
                new BigDecimal("120000"),
                usedAt
        );

        assertThat(ledger.getPublicId()).isNotNull();
        assertThat(ledger.getUsageType()).isEqualTo("PURCHASE");
        assertThat(ledger.getAmount()).isEqualByComparingTo("120000");
        assertThat(ledger.getUsedAt()).isEqualTo(usedAt);

        assertThatThrownBy(() -> CreditUsageLedger.purchase(null, ORDER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ONE, usedAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreditUsageLedger.purchase(CREDIT_LIMIT_PUBLIC_ID, null, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ONE, usedAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreditUsageLedger.purchase(CREDIT_LIMIT_PUBLIC_ID, ORDER_PUBLIC_ID, null,
                BigDecimal.ONE, usedAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreditUsageLedger.purchase(CREDIT_LIMIT_PUBLIC_ID, ORDER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ZERO, usedAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CreditUsageLedger.purchase(CREDIT_LIMIT_PUBLIC_ID, ORDER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                BigDecimal.ONE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void principalRepaymentLedgerUpcomingCreatesLedgerAndValidatesInputs() {
        LocalDate dueDate = LocalDate.of(2026, 12, 31);

        PrincipalRepaymentLedger ledger = PrincipalRepaymentLedger.upcoming(
                CREDIT_LIMIT_PUBLIC_ID,
                ORDER_PUBLIC_ID,
                PAYMENT_REQUEST_PUBLIC_ID,
                dueDate,
                new BigDecimal("120000")
        );

        assertThat(ledger.getPublicId()).isNotNull();
        assertThat(ledger.getDueDate()).isEqualTo(dueDate);
        assertThat(ledger.getPrincipalAmount()).isEqualByComparingTo("120000");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ledger.getStatus()).isEqualTo("UPCOMING");

        assertThatThrownBy(() -> PrincipalRepaymentLedger.upcoming(null, ORDER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                dueDate, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrincipalRepaymentLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, null, PAYMENT_REQUEST_PUBLIC_ID,
                dueDate, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrincipalRepaymentLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, ORDER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PrincipalRepaymentLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, ORDER_PUBLIC_ID, PAYMENT_REQUEST_PUBLIC_ID,
                dueDate, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interestLedgerUpcomingCreatesLedgerAndValidatesInputs() {
        LocalDate dueDate = LocalDate.of(2026, 7, 20);

        InterestLedger ledger = InterestLedger.upcoming(
                CREDIT_LIMIT_PUBLIC_ID,
                new BigDecimal("120000"),
                dueDate,
                new BigDecimal("4500")
        );

        assertThat(ledger.getPublicId()).isNotNull();
        assertThat(ledger.getCreditLimitPublicId()).isEqualTo(CREDIT_LIMIT_PUBLIC_ID);
        assertThat(ledger.getBasePrincipal()).isEqualByComparingTo("120000");
        assertThat(ledger.getDueDate()).isEqualTo(dueDate);
        assertThat(ledger.getInterestAmount()).isEqualByComparingTo("4500");
        assertThat(ledger.getAmountPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ledger.getStatus()).isEqualTo("UPCOMING");

        assertThatThrownBy(() -> InterestLedger.upcoming(null, BigDecimal.ONE, dueDate, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, null, dueDate, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, BigDecimal.ZERO, dueDate, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, BigDecimal.ONE, dueDate, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, BigDecimal.ONE, dueDate, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestLedger.upcoming(CREDIT_LIMIT_PUBLIC_ID, BigDecimal.ONE, null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paymentEventProcessLogProcessedCreatesLogAndValidatesInputs() {
        PaymentEventProcessLog log = PaymentEventProcessLog.processed(
                EVENT_ID,
                PAYMENT_REQUEST_PUBLIC_ID,
                "idem-key-001"
        );

        assertThat(log.getEventId()).isEqualTo(EVENT_ID);
        assertThat(log.getPaymentRequestPublicId()).isEqualTo(PAYMENT_REQUEST_PUBLIC_ID);
        assertThat(log.getIdempotencyKey()).isEqualTo("idem-key-001");
        assertThat(log.getStatus()).isEqualTo("PROCESSED");

        assertThatThrownBy(() -> PaymentEventProcessLog.processed(null, PAYMENT_REQUEST_PUBLIC_ID, "key"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PaymentEventProcessLog.processed(EVENT_ID, null, "key"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PaymentEventProcessLog.processed(EVENT_ID, PAYMENT_REQUEST_PUBLIC_ID, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
