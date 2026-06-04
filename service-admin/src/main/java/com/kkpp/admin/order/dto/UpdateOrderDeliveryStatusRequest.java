package com.kkpp.admin.order.dto;

import com.kkpp.admin.order.domain.DeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자 주문 배송 상태 변경 요청")
public record UpdateOrderDeliveryStatusRequest(
        @NotNull(message = "배송 상태는 필수입니다.")
        @Schema(
                description = "변경할 배송 상태",
                example = "SHIPPING",
                allowableValues = {"PREPARING", "SHIPPING", "DELIVERED", "CANCELLED"}
        )
        DeliveryStatus deliveryStatus
) {
}
