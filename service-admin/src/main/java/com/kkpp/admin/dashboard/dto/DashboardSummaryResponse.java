package com.kkpp.admin.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Admin dashboard summary response")
public record DashboardSummaryResponse(
        @Schema(description = "Credit limit applications with PENDING status")
        long pendingCreditReviewCount,

        @Schema(description = "Total BNPL order amount for the current month")
        BigDecimal monthlyBnplPaymentAmount,

        @Schema(description = "Scheduled BNPL repayment amount for the current month")
        BigDecimal monthlyScheduledRepaymentAmount,

        @Schema(description = "Current overdue rate as a percentage")
        BigDecimal currentOverdueRatePercent,

        @Schema(description = "Users with unresolved overdue ledgers")
        long overdueUserCount,

        @Schema(description = "Current BNPL users with ACTIVE or SUSPENDED credit limits")
        long activeBnplUserCount,

        @Schema(description = "Daily BNPL usage for the latest seven days")
        List<DailyBnplUsage> recentSevenDaysBnplUsage,

        @Schema(description = "Latest five BNPL orders")
        List<RecentBnplOrder> recentBnplOrders,

        @Schema(description = "Admin action required counts")
        ActionRequired actionRequired
) {

    @Schema(description = "Daily BNPL usage amount")
    public record DailyBnplUsage(
            @Schema(description = "Usage date")
            LocalDate date,

            @Schema(description = "BNPL order amount for the date")
            BigDecimal amount
    ) {
    }

    @Schema(description = "Recent BNPL order summary")
    public record RecentBnplOrder(
            @Schema(description = "Order public ID")
            UUID orderPublicId,

            @Schema(description = "Order user name")
            String userName,

            @Schema(description = "First product name snapshot")
            String firstProductName,

            @Schema(description = "Order item count")
            long itemCount,

            @Schema(description = "Display title for the order")
            String orderDisplayName,

            @Schema(description = "Order amount")
            BigDecimal amount,

            @Schema(description = "Order date and time")
            LocalDateTime orderedAt
    ) {
    }

    @Schema(description = "Admin action required counts")
    public record ActionRequired(
            @Schema(description = "Pending credit review count")
            long pendingCreditReviewCount,

            @Schema(description = "Products sold out or out of stock")
            long outOfStockProductCount,

            @Schema(description = "Unresolved overdue ledger count")
            long overdueIssueCount
    ) {
    }
}
