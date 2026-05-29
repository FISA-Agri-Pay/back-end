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
        UUID paymentRequestPublicId,
        UUID userPublicId,
        UUID orderPublicId,
        BigDecimal totalAmount,
        String idempotencyKey
) {
}
