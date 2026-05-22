package com.kkpp.catalog.product.dto.response;

import com.kkpp.catalog.product.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "상품 목록 조회 응답")
public record ProductSummaryResponse(
        @Schema(description = "외부 노출용 상품 ID", example = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1")
        UUID productId,
        @Schema(description = "상품명", example = "유기질 비료 20kg")
        String name,
        @Schema(description = "카테고리명", example = "비료")
        String categoryName,
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

    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getPublicId(),
                product.getName(),
                product.getCategory().getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getUnit(),
                product.getImageUrl(),
                product.getStatus()
        );
    }
}
