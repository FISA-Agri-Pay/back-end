package com.kkpp.admin.product.dto;

import java.util.List;

// 상품 목록 페이지네이션 응답 DTO임
public record ProductPageResponse(
        List<ProductResponse> products,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
