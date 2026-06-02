package com.kkpp.admin.bnpl.dto;

import java.time.Instant;

// 연체 알림 일괄 발송 결과 중 사용자별 건 응답 DTO
public record OverdueAlertItemResponse(
        String userId,
        String status,
        Instant sentAt,
        String reason
) {
}
