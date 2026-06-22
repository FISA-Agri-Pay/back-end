package com.kkpp.admin.credit.dto;

import java.util.List;

// 한도 심사 목록 조회의 페이지 응답 DTO
// 관리자 화면의 목록 테이블과 페이지네이션 UI를 구성하는 데 필요한 값을 담는다.
public record CreditReviewPageResponse(
        List<CreditReviewSummaryResponse> reviews,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
