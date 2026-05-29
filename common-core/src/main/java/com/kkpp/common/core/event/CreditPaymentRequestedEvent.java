package com.kkpp.common.core.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CreditPaymentRequestedEvent(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        UUID paymentRequestPublicId,
        UUID userPublicId,
        UUID orderPublicId,
        BigDecimal totalAmount,
        DeliveryAddress deliveryAddress,
        List<Item> items,
        String idempotencyKey
) {
    public CreditPaymentRequestedEvent {
        items = List.copyOf(items);
    }

    public record DeliveryAddress(
            String recipientName,
            String recipientPhone,
            String address,
            String addressDetail,
            String zipCode
    ) {
    }

    public record Item(
            UUID productPublicId,
            String productNameSnapshot,
            BigDecimal unitPriceSnapshot,
            Integer quantity,
            BigDecimal totalPrice
    ) {
    }
}
