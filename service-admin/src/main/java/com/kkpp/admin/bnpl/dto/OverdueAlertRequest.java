package com.kkpp.admin.bnpl.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

// 연체 알림 일괄 발송 요청 DTO
// POST /api/v1/admin/bnpl/overdue/alerts
// userPublicIds 미입력 시 전체 미해소 연체자 대상으로 발송된다.
public record OverdueAlertRequest(
        List<String> userPublicIds,
        @NotBlank String channel,
        String message
) {
}
