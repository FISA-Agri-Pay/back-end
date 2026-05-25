package com.kkpp.common.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CreditPaymentRequestedEventTest {

    @Test
    void constructsEventWithAllFields() {
        String eventId = "evt-001";
        String eventType = "CreditPaymentRequested";
        LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 1, 10, 0);
        UUID checkoutRequestId = UUID.randomUUID();
        UUID userPublicId = UUID.randomUUID();
        BigDecimal totalAmount = new BigDecimal("150000.00");
        String idempotencyKey = "idem-key-001";

        CreditPaymentRequestedEvent.DeliveryAddress address = new CreditPaymentRequestedEvent.DeliveryAddress(
                "홍길동", "01012345678", "서울시 강남구 테헤란로 123", "101호", "06134"
        );
        CreditPaymentRequestedEvent.Item item = new CreditPaymentRequestedEvent.Item(
                UUID.randomUUID(), "유기질 비료 20kg", new BigDecimal("75000.00"), 2, new BigDecimal("150000.00")
        );

        CreditPaymentRequestedEvent event = new CreditPaymentRequestedEvent(
                eventId, eventType, occurredAt, checkoutRequestId, userPublicId,
                totalAmount, address, List.of(item), idempotencyKey
        );

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo(eventType);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.checkoutRequestId()).isEqualTo(checkoutRequestId);
        assertThat(event.userPublicId()).isEqualTo(userPublicId);
        assertThat(event.totalAmount()).isEqualByComparingTo(totalAmount);
        assertThat(event.deliveryAddress()).isEqualTo(address);
        assertThat(event.items()).hasSize(1);
        assertThat(event.idempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    void constructsDeliveryAddressWithAllFields() {
        CreditPaymentRequestedEvent.DeliveryAddress address = new CreditPaymentRequestedEvent.DeliveryAddress(
                "김농부", "01098765432", "경기도 수원시 영통구 광교로 1", "B동 203호", "16229"
        );

        assertThat(address.recipientName()).isEqualTo("김농부");
        assertThat(address.recipientPhone()).isEqualTo("01098765432");
        assertThat(address.address()).isEqualTo("경기도 수원시 영통구 광교로 1");
        assertThat(address.addressDetail()).isEqualTo("B동 203호");
        assertThat(address.zipCode()).isEqualTo("16229");
    }

    @Test
    void constructsItemWithAllFields() {
        UUID productId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");
        BigDecimal unitPrice = new BigDecimal("28000.00");
        Integer quantity = 3;
        BigDecimal totalPrice = new BigDecimal("84000.00");

        CreditPaymentRequestedEvent.Item item = new CreditPaymentRequestedEvent.Item(
                productId, "유기질 비료 20kg", unitPrice, quantity, totalPrice
        );

        assertThat(item.productId()).isEqualTo(productId);
        assertThat(item.productName()).isEqualTo("유기질 비료 20kg");
        assertThat(item.unitPrice()).isEqualByComparingTo(unitPrice);
        assertThat(item.quantity()).isEqualTo(quantity);
        assertThat(item.totalPrice()).isEqualByComparingTo(totalPrice);
    }

    @Test
    void supportsMultipleItems() {
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();

        CreditPaymentRequestedEvent.Item item1 = new CreditPaymentRequestedEvent.Item(
                productId1, "비료 A", new BigDecimal("10000.00"), 1, new BigDecimal("10000.00")
        );
        CreditPaymentRequestedEvent.Item item2 = new CreditPaymentRequestedEvent.Item(
                productId2, "농약 B", new BigDecimal("20000.00"), 2, new BigDecimal("40000.00")
        );

        CreditPaymentRequestedEvent event = new CreditPaymentRequestedEvent(
                "evt-002", "CreditPaymentRequested", LocalDateTime.now(),
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("50000.00"),
                new CreditPaymentRequestedEvent.DeliveryAddress("홍길동", "01000000000", "주소", null, "12345"),
                List.of(item1, item2),
                "idem-key-002"
        );

        assertThat(event.items()).hasSize(2);
        assertThat(event.items().get(0).productName()).isEqualTo("비료 A");
        assertThat(event.items().get(1).productName()).isEqualTo("농약 B");
    }

    @Test
    void supportsEmptyItemList() {
        CreditPaymentRequestedEvent event = new CreditPaymentRequestedEvent(
                "evt-003", "CreditPaymentRequested", LocalDateTime.now(),
                UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ZERO,
                new CreditPaymentRequestedEvent.DeliveryAddress("홍길동", "01000000000", "주소", null, "12345"),
                List.of(),
                "idem-key-003"
        );

        assertThat(event.items()).isEmpty();
        assertThat(event.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deliveryAddressNullAddressDetailIsAllowed() {
        CreditPaymentRequestedEvent.DeliveryAddress address = new CreditPaymentRequestedEvent.DeliveryAddress(
                "홍길동", "01012345678", "서울시 강남구", null, "06134"
        );

        assertThat(address.addressDetail()).isNull();
    }

    @Test
    void recordEqualityBasedOnFieldValues() {
        UUID checkoutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 12, 0);

        CreditPaymentRequestedEvent event1 = new CreditPaymentRequestedEvent(
                "evt-eq", "CreditPaymentRequested", now, checkoutId, userId,
                new BigDecimal("100.00"),
                new CreditPaymentRequestedEvent.DeliveryAddress("이름", "전화", "주소", null, "우편번호"),
                List.of(),
                "key"
        );
        CreditPaymentRequestedEvent event2 = new CreditPaymentRequestedEvent(
                "evt-eq", "CreditPaymentRequested", now, checkoutId, userId,
                new BigDecimal("100.00"),
                new CreditPaymentRequestedEvent.DeliveryAddress("이름", "전화", "주소", null, "우편번호"),
                List.of(),
                "key"
        );

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }
}