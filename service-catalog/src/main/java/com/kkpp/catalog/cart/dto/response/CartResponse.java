package com.kkpp.catalog.cart.dto.response;

import com.kkpp.catalog.cart.domain.CartItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "장바구니 조회 응답")
public record CartResponse(
        @Schema(description = "장바구니 항목 목록")
        List<CartItemResponse> items,
        @Schema(description = "장바구니 총 금액", example = "1500000")
        BigDecimal totalAmount
) {

    public static CartResponse from(List<CartItem> cartItems) {
        List<CartItemResponse> items = cartItems.stream()
                .map(CartItemResponse::from)
                .toList();
        BigDecimal totalAmount = items.stream()
                .map(CartItemResponse::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, totalAmount);
    }
}
