package com.kkpp.admin.bnpl.dto;

import java.time.Instant;

// 상환 알림 단건 발송 응답 DTO
// POST /api/v1/admin/bnpl/users/{userPublicId}/repayment-alert
public record RepaymentAlertResponse(
        Instant sentAt,
        String channel
) {
}
