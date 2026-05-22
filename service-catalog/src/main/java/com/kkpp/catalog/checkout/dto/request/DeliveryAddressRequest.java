package com.kkpp.catalog.checkout.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "배송지 요청")
public record DeliveryAddressRequest(
        @Schema(description = "수령인 이름", example = "송환")
        @NotBlank String recipientName,
        @Schema(description = "수령인 휴대폰 번호", example = "010-1234-1234")
        @NotBlank String recipientPhone,
        @Schema(description = "배송 주소", example = "경북 안동시 농촌마을길 12-3")
        @NotBlank String address,
        @Schema(description = "상세 주소", example = "창고 앞")
        String addressDetail,
        @Schema(description = "우편번호", example = "36700")
        @NotBlank String zipCode
) {
}
