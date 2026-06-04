package com.kkpp.core.credithistory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "외상 이용 내역 응답")
public record CreditUsageHistoryResponse(
        @Schema(description = "외상 이용 원장 publicId", example = "11111111-1111-4111-8111-111111111111")
        UUID historyPublicId,

        @Schema(description = "외상 이용 일시", example = "2026-05-11T10:00:00")
        LocalDateTime usedAt,

        @Schema(description = "화면 표시용 이용 내역명", example = "드론 방제 서비스 (1,000평)")
        String title,

        @Schema(description = "표시 금액. 구매성 외상 이용은 음수로 응답", example = "-100000.00")
        BigDecimal amount,

        @Schema(description = "외상 이용 유형", example = "PURCHASE")
        String usageType,

        @Schema(description = "주문 상태", example = "CONFIRMED")
        String orderStatus,

        @Schema(description = "배송 상태", example = "SHIPPING")
        String deliveryStatus,

        @Schema(description = "화면 표시용 상태", example = "배송중")
        String displayStatus
) {
}
