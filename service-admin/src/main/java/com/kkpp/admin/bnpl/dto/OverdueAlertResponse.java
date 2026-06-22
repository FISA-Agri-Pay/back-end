package com.kkpp.admin.bnpl.dto;

import java.util.List;

// 연체 알림 일괄 발송 결과 응답 DTO
// POST /api/v1/admin/bnpl/overdue/alerts
public record OverdueAlertResponse(
        int totalCount,
        int successCount,
        int failCount,
        List<OverdueAlertItemResponse> results
) {
}
