package com.kkpp.admin.product.dto;

import com.kkpp.admin.product.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// 상품 부분 수정 요청 DTO임
public record UpdateProductRequest(
        Long categoryId,
        @Size(max = 100) String name,
        String description,
        @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal price,
        @Min(0) Integer stockQuantity,
        @Size(max = 20) String unit,
        @Size(max = 500) String imageUrl,
        ProductStatus status
) {
}
