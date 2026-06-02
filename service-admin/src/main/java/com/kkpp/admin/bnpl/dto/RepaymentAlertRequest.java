package com.kkpp.admin.bnpl.dto;

import jakarta.validation.constraints.NotBlank;

// 상환 알림 단건 발송 요청 DTO
// POST /api/v1/admin/bnpl/users/{userPublicId}/repayment-alert
public record RepaymentAlertRequest(
        @NotBlank String channel,
        String message
) {
}
