package com.kkpp.admin.bnpl.dto;

import java.math.BigDecimal;

// BNPL 이용 현황 상단 KPI 카드 응답 DTO
// GET /api/v1/admin/bnpl/summary
public record BnplSummaryResponse(
        BigDecimal totalBalance,
        BigDecimal scheduledRepayment,
        BigDecimal overdueAmount,
        boolean isOverdueAlert
) {
}
