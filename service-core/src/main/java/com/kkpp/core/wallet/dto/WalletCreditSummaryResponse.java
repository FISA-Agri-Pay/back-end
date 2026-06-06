package com.kkpp.core.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "홈 화면 한도 요약 조회 응답")
public record WalletCreditSummaryResponse(
        @Schema(description = "활성 한도 보유 여부", example = "true")
        boolean hasActiveLimit,

        @Schema(description = "활성 한도 publicId", example = "11111111-1111-4111-8111-111111111111")
        UUID creditLimitPublicId,

        @Schema(description = "총 승인 한도", example = "4000000.00")
        BigDecimal totalLimit,

        @Schema(description = "현재 외상 금액 및 사용 금액", example = "2500000.00")
        BigDecimal usedAmount,

        @Schema(description = "잔여 한도", example = "1500000.00")
        BigDecimal remainingAmount,

        @Schema(description = "한도 사용률", example = "62.5")
        BigDecimal usageRate,

        @Schema(description = "한도 상태 (ACTIVE 등)", example = "ACTIVE")
        String status,

        @Schema(description = "한도 신청 상태 (REQUESTED | PENDING | APPROVED | REJECTED | CANCELLED | null=신청 없음)", example = "PENDING")
        String applicationStatus
) {
}
