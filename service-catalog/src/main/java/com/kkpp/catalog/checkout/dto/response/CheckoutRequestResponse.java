package com.kkpp.catalog.checkout.dto.response;

import com.kkpp.catalog.checkout.domain.BnplPaymentRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "외상 결제 요청 응답")
public record CheckoutRequestResponse(
        @Schema(description = "BNPL 결제요청 publicId", example = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1")
        UUID paymentRequestPublicId,
        @Schema(description = "service-core가 주문 생성에 사용할 주문 publicId", example = "dddddddd-dddd-4ddd-8ddd-dddddddddddd")
        UUID orderPublicId,
        @Schema(description = "결제 요청 상태", example = "REQUESTED", allowableValues = {"REQUESTED", "APPROVED", "REJECTED", "CANCELLED"})
        String status,
        @Schema(description = "결제 요청 총 금액", example = "1500000")
        BigDecimal totalAmount,
        @Schema(description = "결제 거절 메시지", example = "사용 가능한 외상 한도가 부족합니다.")
        String rejectionReason
) {

    public static CheckoutRequestResponse from(BnplPaymentRequest paymentRequest, UUID orderPublicId) {
        return new CheckoutRequestResponse(
                paymentRequest.getPublicId(),
                orderPublicId,
                paymentRequest.getRequestStatus(),
                paymentRequest.getTotalAmount(),
                paymentRequest.getRejectionReason()
        );
    }
}
