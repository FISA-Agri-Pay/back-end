package com.kkpp.catalog.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "장바구니 수량 변경 요청")
public record UpdateCartItemQuantityRequest(
        @Schema(description = "변경할 수량", example = "5")
        @NotNull @Min(1) Integer quantity
) {
}
