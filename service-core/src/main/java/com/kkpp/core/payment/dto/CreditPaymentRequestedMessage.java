package com.kkpp.core.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditPaymentRequestedMessage(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        @JsonAlias("checkoutRequestId")
        UUID paymentRequestPublicId,
        Long userId,
        UUID userPublicId,
        Long orderId,
        UUID orderPublicId,
        BigDecimal totalAmount,
        String idempotencyKey
) {
    public UUID checkoutRequestId() {
        return paymentRequestPublicId;
    }
}
