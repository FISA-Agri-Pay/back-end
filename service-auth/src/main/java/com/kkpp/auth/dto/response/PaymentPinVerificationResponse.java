package com.kkpp.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PaymentPinVerificationResponse(
        UUID verificationId,
        Instant expiresAt
) {
}
