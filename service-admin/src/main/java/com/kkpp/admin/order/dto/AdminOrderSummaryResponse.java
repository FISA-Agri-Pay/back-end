package com.kkpp.admin.order.dto;

import com.kkpp.admin.order.domain.AdminOrder;
import com.kkpp.admin.order.domain.AdminOrderUser;
import com.kkpp.admin.order.domain.DeliveryStatus;
import com.kkpp.admin.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "관리자 주문 목록 항목 응답")
public record AdminOrderSummaryResponse(
        @Schema(description = "주문 공개 ID")
        UUID orderPublicId,

        @Schema(description = "사용자 공개 ID")
        UUID userPublicId,

        @Schema(description = "사용자 이름")
        String userName,

        @Schema(description = "사용자 휴대폰 번호")
        String userPhone,

        @Schema(description = "주문 금액")
        BigDecimal totalAmount,

        @Schema(description = "주문 상태", allowableValues = {"CONFIRMED", "CANCELLED"})
        OrderStatus orderStatus,

        @Schema(description = "배송 상태", allowableValues = {"PREPARING", "SHIPPING", "DELIVERED", "CANCELLED"})
        DeliveryStatus deliveryStatus,

        @Schema(description = "주문 일시")
        LocalDateTime orderedAt,

        @Schema(description = "수령인 이름")
        String recipientName,

        @Schema(description = "수령인 연락처")
        String recipientPhone
) {

    public static AdminOrderSummaryResponse from(AdminOrder order) {
        AdminOrderUser user = order.getUser();
        return new AdminOrderSummaryResponse(
                order.getPublicId(),
                order.getUserPublicId(),
                user.getName(),
                user.getPhone(),
                order.getTotalAmount(),
                order.getOrderStatus(),
                order.getDeliveryStatus(),
                order.getOrderedAt(),
                order.getRecipientName(),
                order.getRecipientPhone()
        );
    }
}
