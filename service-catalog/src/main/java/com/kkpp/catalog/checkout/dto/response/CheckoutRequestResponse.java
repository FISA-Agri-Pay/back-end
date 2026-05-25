package com.kkpp.catalog.checkout.dto.response;

import com.kkpp.catalog.checkout.domain.CheckoutRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "외상 결제 요청 응답")
public record CheckoutRequestResponse(
        @Schema(description = "외부 노출용 결제 요청 ID", example = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1")
        UUID checkoutRequestId,
        @Schema(description = "결제 요청 상태", example = "PENDING", allowableValues = {"PENDING", "APPROVED", "REJECTED", "EXPIRED", "CANCELLED"})
        String status,
        @Schema(description = "결제 요청 총 금액", example = "1500000")
        BigDecimal totalAmount,
        @Schema(description = "결제 수단", example = "CREDIT_LIMIT")
        String paymentMethod,
        @Schema(description = "온프레미스 결제 승인 후 확정된 주문 번호", example = "20260520-0012")
        String orderId,
        @Schema(description = "결제 거절 사유 코드", example = "LIMIT_EXCEEDED")
        String rejectReasonCode,
        @Schema(description = "결제 거절 메시지", example = "사용 가능한 외상 한도가 부족합니다.")
        String rejectMessage
) {

    public static CheckoutRequestResponse from(CheckoutRequest checkoutRequest) {
        return new CheckoutRequestResponse(
                checkoutRequest.getPublicId(),
                checkoutRequest.getStatus(),
                checkoutRequest.getTotalAmount(),
                checkoutRequest.getPaymentMethod(),
                checkoutRequest.getOrderId(),
                checkoutRequest.getRejectReasonCode(),
                checkoutRequest.getRejectMessage()
        );
    }
}
