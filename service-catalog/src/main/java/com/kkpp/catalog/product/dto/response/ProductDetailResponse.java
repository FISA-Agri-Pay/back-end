package com.kkpp.catalog.product.dto.response;

import com.kkpp.catalog.product.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "상품 상세 조회 응답")
public record ProductDetailResponse(
        @Schema(description = "외부 노출용 상품 ID", example = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1")
        UUID productId,
        @Schema(description = "상품명", example = "유기질 비료 20kg")
        String name,
        @Schema(description = "카테고리명", example = "비료")
        String categoryName,
        @Schema(description = "상품 설명", example = "과수와 밭작물에 사용하는 범용 유기질 비료")
        String description,
        @Schema(description = "상품 단가", example = "28000")
        BigDecimal price,
        @Schema(description = "재고 수량", example = "120")
        Integer stockQuantity,
        @Schema(description = "판매 단위", example = "포")
        String unit,
        @Schema(description = "상품 이미지 URL", example = "https://local-images.kongkongfarm/fertilizer-20kg.png")
        String imageUrl,
        @Schema(description = "상품 판매 상태", example = "ON_SALE", allowableValues = {"ON_SALE", "OUT_OF_STOCK", "HIDDEN"})
        String status
) {

    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getPublicId(),
                product.getName(),
                product.getCategory().getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getUnit(),
                product.getImageUrl(),
                product.getStatus()
        );
    }
}
