package com.kkpp.common.core.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderConfirmedEvent(
        Long orderId,
        Long userId,
        Long creditLimitId,
        BigDecimal amount,
        LocalDateTime occurredAt
) {
}
