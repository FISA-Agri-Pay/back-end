package com.kkpp.catalog.cart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "장바구니 담기 요청")
public record AddCartItemRequest(
        @Schema(description = "외부 노출용 상품 ID", example = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1")
        @NotNull UUID productId,
        @Schema(description = "담을 수량", example = "2")
        @NotNull @Min(1) Integer quantity
) {
}
