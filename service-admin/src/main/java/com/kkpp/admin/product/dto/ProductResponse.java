package com.kkpp.admin.product.dto;

import com.kkpp.admin.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// 관리자 상품 목록과 수정 결과에 사용하는 상품 응답 DTO임
public record ProductResponse(
        UUID publicId,
        String productNumber,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String unit,
        String imageUrl,
        ProductStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
