package com.kkpp.auth.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentPinVerifiedEvent(
        UUID eventId,
        UUID verificationId,
        UUID userPublicId,
        Instant verifiedAt,
        Instant expiresAt,
        String verificationType
) {
}
