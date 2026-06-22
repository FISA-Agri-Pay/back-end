package com.kkpp.admin.bnpl.dto;

import java.math.BigDecimal;

// 연체 현황 상단 KPI 카드 응답 DTO
// GET /api/v1/admin/bnpl/overdue/summary
public record OverdueSummaryResponse(
        long totalOverdueUsers,
        BigDecimal totalOverdueAmount,
        BigDecimal totalPenaltyAmount,
        OverdueByStage overdueByStage
) {

    // 단계별 연체 회원 수 내부 DTO
    public record OverdueByStage(long STAGE_1, long STAGE_2, long STAGE_3) {
    }
}
