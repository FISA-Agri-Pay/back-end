package com.kkpp.payment.testsupport;

import com.kkpp.payment.domain.CreditLimit;
import com.kkpp.payment.domain.Order;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class PaymentTestEntityFactory {

    public static final UUID EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID PAYMENT_REQUEST_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final UUID USER_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    public static final UUID ORDER_PUBLIC_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    public static final UUID CREDIT_LIMIT_PUBLIC_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    public static final UUID APPLICATION_PUBLIC_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    public static final UUID PRODUCT_PUBLIC_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");

    private PaymentTestEntityFactory() {
    }

    public static CreditPaymentRequestedMessage message() {
        return new CreditPaymentRequestedMessage(
                EVENT_ID.toString(),
                "CREDIT_PAYMENT_REQUESTED",
                LocalDateTime.of(2026, 6, 14, 10, 30),
                PAYMENT_REQUEST_PUBLIC_ID,
                USER_PUBLIC_ID,
                ORDER_PUBLIC_ID,
                new BigDecimal("120000"),
                deliveryAddress(),
                List.of(item()),
                "idem-key-001"
        );
    }

    public static CreditPaymentRequestedMessage messageWith(
            String eventId,
            UUID paymentRequestPublicId,
            UUID userPublicId,
            UUID orderPublicId,
            BigDecimal totalAmount,
            CreditPaymentRequestedMessage.DeliveryAddress deliveryAddress
    ) {
        return new CreditPaymentRequestedMessage(
                eventId,
                "CREDIT_PAYMENT_REQUESTED",
                LocalDateTime.of(2026, 6, 14, 10, 30),
                paymentRequestPublicId,
                userPublicId,
                orderPublicId,
                totalAmount,
                deliveryAddress,
                List.of(item()),
                "idem-key-001"
        );
    }

    public static CreditPaymentRequestedMessage.DeliveryAddress deliveryAddress() {
        return new CreditPaymentRequestedMessage.DeliveryAddress(
                "홍길동",
                "010-0000-0000",
                "경기도 안성시",
                "101동",
                "17500"
        );
    }

    public static CreditPaymentRequestedMessage.Item item() {
        return new CreditPaymentRequestedMessage.Item(
                PRODUCT_PUBLIC_ID,
                "유기질 비료",
                new BigDecimal("40000"),
                3,
                new BigDecimal("120000")
        );
    }

    public static Order order() {
        return Order.confirmed(
                ORDER_PUBLIC_ID,
                USER_PUBLIC_ID,
                PAYMENT_REQUEST_PUBLIC_ID,
                new BigDecimal("120000"),
                deliveryAddress(),
                List.of(item()),
                LocalDateTime.of(2026, 6, 14, 10, 30)
        );
    }

    public static CreditLimit activeCreditLimit() {
        return creditLimit("ACTIVE", new BigDecimal("500000"), BigDecimal.ZERO, LocalDate.now().plusMonths(1));
    }

    public static CreditLimit creditLimit(String status, BigDecimal totalLimit, BigDecimal usedAmount, LocalDate expiresAt) {
        CreditLimit creditLimit = instantiate(CreditLimit.class);
        set(creditLimit, "id", 1L);
        set(creditLimit, "publicId", CREDIT_LIMIT_PUBLIC_ID);
        set(creditLimit, "userPublicId", USER_PUBLIC_ID);
        set(creditLimit, "applicationPublicId", APPLICATION_PUBLIC_ID);
        set(creditLimit, "cropTypeSnapshot", "RICE");
        set(creditLimit, "totalLimit", totalLimit);
        set(creditLimit, "usedAmount", usedAmount);
        set(creditLimit, "interestRate", new BigDecimal("0.0450"));
        set(creditLimit, "interestDueDay", 20);
        set(creditLimit, "principalDueDate", LocalDate.of(2026, 12, 31));
        set(creditLimit, "expiresAt", expiresAt);
        set(creditLimit, "status", status);
        return creditLimit;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot instantiate " + type.getSimpleName(), exception);
        }
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot set " + fieldName, exception);
        }
    }
}
