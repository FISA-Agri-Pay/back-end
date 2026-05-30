package com.kkpp.admin.bnpl.dto;

import java.util.List;

// 연체 대상자 목록 페이지 응답 DTO
// GET /api/v1/admin/bnpl/overdue/users
public record OverdueUserPageResponse(
        List<OverdueUserSummaryResponse> users,
        PaginationInfo pagination
) {
}
