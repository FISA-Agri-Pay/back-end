package com.kkpp.admin.bnpl.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// 사용자별 BNPL 이용 현황 목록 한 행 응답 DTO
// GET /api/v1/admin/bnpl/users
public record BnplUserSummaryResponse(
        UUID userId,
        String userName,
        String phone,
        BigDecimal creditLimit,
        BigDecimal usedAmount,
        BigDecimal overdueAmount,
        LocalDate nextRepaymentDate,
        boolean isOverdue,
        String status
) {
}
