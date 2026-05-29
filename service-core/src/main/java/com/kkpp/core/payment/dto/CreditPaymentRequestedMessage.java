package com.kkpp.core.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreditPaymentRequestedMessage(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        UUID checkoutRequestId,
        Long userId,
        UUID userPublicId,
        Long orderId,
        UUID orderPublicId,
        UUID paymentRequestPublicId,
        BigDecimal totalAmount,
        String idempotencyKey
) {
}
