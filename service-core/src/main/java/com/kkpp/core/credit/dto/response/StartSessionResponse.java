package com.kkpp.core.credit.dto.response;

import java.time.Instant;

public record StartSessionResponse(
        String sessionId,
        Instant expiresAt
) {}
