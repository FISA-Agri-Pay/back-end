package com.kkpp.catalog.cart.dto.response;

import com.kkpp.catalog.cart.domain.CartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "장바구니 항목 응답")
public record CartItemResponse(
        @Schema(description = "장바구니 항목 ID", example = "1")
        Long cartItemId,
        @Schema(description = "외부 노출용 상품 ID", example = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1")
        UUID productId,
        @Schema(description = "상품명", example = "유기질 비료 20kg")
        String productName,
        @Schema(description = "카테고리명", example = "비료")
        String categoryName,
        @Schema(description = "상품 단가", example = "28000")
        BigDecimal unitPrice,
        @Schema(description = "장바구니 수량", example = "3")
        Integer quantity,
        @Schema(description = "항목 총 금액", example = "84000")
        BigDecimal totalPrice,
        @Schema(description = "판매 단위", example = "포")
        String unit,
        @Schema(description = "상품 이미지 URL", example = "https://local-images.kongkongfarm/fertilizer-20kg.png")
        String imageUrl,
        @Schema(description = "상품 판매 상태", example = "ON_SALE")
        String productStatus
) {

    public static CartItemResponse from(CartItem cartItem) {
        BigDecimal totalPrice = cartItem.getProduct().getPrice()
                .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getProduct().getPublicId(),
                cartItem.getProduct().getName(),
                cartItem.getProduct().getCategory().getName(),
                cartItem.getProduct().getPrice(),
                cartItem.getQuantity(),
                totalPrice,
                cartItem.getProduct().getUnit(),
                cartItem.getProduct().getImageUrl(),
                cartItem.getProduct().getStatus()
        );
    }
}
