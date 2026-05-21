package com.kkpp.core.credit.dto.response;

import java.time.LocalDateTime;

public record StartSessionResponse(
        String sessionId,
        LocalDateTime expiresAt
) {}
