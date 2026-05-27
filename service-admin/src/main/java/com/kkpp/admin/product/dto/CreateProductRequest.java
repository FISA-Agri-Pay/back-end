package com.kkpp.admin.product.dto;

import com.kkpp.admin.product.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// 신규 상품 등록 요청 DTO임
public record CreateProductRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer stockQuantity,
        @NotBlank @Size(max = 20) String unit,
        @Size(max = 500) String imageUrl,
        ProductStatus status
) {
}
