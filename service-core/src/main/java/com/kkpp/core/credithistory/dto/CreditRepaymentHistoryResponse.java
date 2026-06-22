package com.kkpp.core.credithistory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "상환 및 납부 내역 응답")
public record CreditRepaymentHistoryResponse(
        @Schema(description = "지갑 거래 publicId", example = "22222222-2222-4222-8222-222222222222")
        UUID transactionPublicId,

        @Schema(description = "거래 일시", example = "2026-05-11T10:00:00")
        LocalDateTime transactedAt,

        @Schema(description = "화면 표시용 거래명", example = "4월 이자 상환")
        String title,

        @Schema(description = "거래 유형", example = "INTEREST_PAYMENT")
        String transactionType,

        @Schema(description = "표시 금액. 상환성 거래는 음수로 응답", example = "-100000.00")
        BigDecimal amount
) {
}
