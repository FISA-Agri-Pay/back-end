package com.kkpp.admin.bnpl.dto;

// 관리자 BNPL 목록 API 공통 페이지네이션 응답 DTO
// API 명세의 currentPage는 1부터 시작하는 값이다 (Spring Data의 0-indexed와 다름).
public record PaginationInfo(
        int currentPage,
        int totalPages,
        long totalCount
) {
}
