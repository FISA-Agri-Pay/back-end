package com.kkpp.admin.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "관리자 대시보드 요약 응답")
public record DashboardSummaryResponse(
        @Schema(description = "한도 심사 대기 건수")
        long pendingCreditReviewCount,

        @Schema(description = "당월 외상 결제 총액")
        BigDecimal monthlyBnplPaymentAmount,

        @Schema(description = "당월 상환 예정 금액")
        BigDecimal monthlyScheduledRepaymentAmount,

        @Schema(description = "현재 연체율")
        BigDecimal currentOverdueRatePercent,

        @Schema(description = "미해소 연체 사용자 수")
        long overdueUserCount,

        @Schema(description = "현재 BNPL 이용 사용자 수")
        long activeBnplUserCount,

        @Schema(description = "최근 7일 외상 이용 추이")
        List<DailyBnplUsage> recentSevenDaysBnplUsage,

        @Schema(description = "최근 접수 외상 주문 목록")
        List<RecentBnplOrder> recentBnplOrders,

        @Schema(description = "관리자 처리 필요 업무 건수")
        ActionRequired actionRequired
) {

    @Schema(description = "일자별 외상 이용 금액")
    public record DailyBnplUsage(
            @Schema(description = "이용 일자")
            LocalDate date,

            @Schema(description = "해당 일자의 외상 이용 금액")
            BigDecimal amount
    ) {
    }

    @Schema(description = "최근 외상 주문 요약")
    public record RecentBnplOrder(
            @Schema(description = "주문 공개 ID")
            UUID orderPublicId,

            @Schema(description = "주문자명")
            String userName,

            @Schema(description = "대표 상품명")
            String firstProductName,

            @Schema(description = "주문 상품 수")
            long itemCount,

            @Schema(description = "주문 표시명")
            String orderDisplayName,

            @Schema(description = "주문 금액")
            BigDecimal amount,

            @Schema(description = "주문 일시")
            LocalDateTime orderedAt
    ) {
    }

    @Schema(description = "관리자 처리 필요 업무 건수")
    public record ActionRequired(
            @Schema(description = "한도 심사 대기 건수")
            long pendingCreditReviewCount,

            @Schema(description = "품절 또는 재고 부족 상품 건수")
            long outOfStockProductCount,

            @Schema(description = "미해소 연체 건수")
            long overdueIssueCount
    ) {
    }
}
