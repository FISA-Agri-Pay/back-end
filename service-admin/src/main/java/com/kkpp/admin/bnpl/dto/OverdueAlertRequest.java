package com.kkpp.admin.bnpl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

// 연체 알림 일괄 발송 요청 DTO
// POST /api/v1/admin/bnpl/overdue/alerts
// userPublicIds 미입력 시 전체 미해소 연체자 대상으로 발송된다.
public record OverdueAlertRequest(
        List<
                @NotBlank(message = "userPublicIds must not contain blank values.")
                @Pattern(
                        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                        message = "userPublicIds must contain valid UUID values."
                )
                String
        > userPublicIds,
        @NotBlank String channel,
        String message
) {
}
