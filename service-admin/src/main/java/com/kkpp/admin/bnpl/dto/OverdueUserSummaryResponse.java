package com.kkpp.admin.bnpl.dto;

import com.kkpp.admin.bnpl.domain.OverdueStage;
import com.kkpp.admin.bnpl.domain.OverdueType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// 연체 대상자 목록 한 행 응답 DTO
// GET /api/v1/admin/bnpl/overdue/users
public record OverdueUserSummaryResponse(
        UUID userId,
        String userName,
        String phone,
        OverdueType overdueType,
        BigDecimal overdueAmount,
        BigDecimal penaltyAmount,
        Integer overdueDays,
        OverdueStage stage,
        LocalDate dueDate,
        UUID orderId,
        UUID paymentRequestId,
        LocalDateTime alertSentAt
) {
}
