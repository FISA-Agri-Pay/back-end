package com.kkpp.admin.bnpl.dto;

import java.util.List;

// 사용자별 BNPL 이용 현황 목록 페이지 응답 DTO
// GET /api/v1/admin/bnpl/users
public record BnplUserPageResponse(
        List<BnplUserSummaryResponse> users,
        PaginationInfo pagination
) {
}
