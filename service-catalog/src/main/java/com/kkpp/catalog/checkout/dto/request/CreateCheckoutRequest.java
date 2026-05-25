package com.kkpp.catalog.checkout.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "외상 결제 요청 생성 요청")
public record CreateCheckoutRequest(
        @Schema(description = "결제할 장바구니 항목 ID 목록", example = "[1, 2]")
        @NotEmpty List<Long> cartItemIds,
        @Schema(description = "배송지 정보")
        @NotNull @Valid DeliveryAddressRequest deliveryAddress,
        @Schema(description = "결제 수단. 현재 외상 한도 결제만 지원합니다.", example = "CREDIT_LIMIT", allowableValues = {"CREDIT_LIMIT"})
        @NotBlank String paymentMethod,
        @Schema(description = "중복 결제 요청 방지 키. 같은 사용자의 같은 키는 같은 결제 요청으로 처리합니다.", example = "checkout-20260520-user1-001")
        @NotBlank String idempotencyKey
) {
}
