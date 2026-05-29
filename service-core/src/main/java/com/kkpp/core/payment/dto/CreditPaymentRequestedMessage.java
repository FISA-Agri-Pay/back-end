package com.kkpp.core.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditPaymentRequestedMessage(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        // 과거 checkoutRequestId 필드로 발행된 이벤트 호환용 alias
        @JsonAlias("checkoutRequestId")
        UUID paymentRequestPublicId,
        UUID userPublicId,
        UUID orderPublicId,
        BigDecimal totalAmount,
        DeliveryAddress deliveryAddress,
        List<Item> items,
        String idempotencyKey
) {
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
