package com.kkpp.admin.dashboard.service;

import com.kkpp.admin.bnpl.repository.BnplCreditLimitRepository;
import com.kkpp.admin.bnpl.repository.BnplOrderRepository;
import com.kkpp.admin.bnpl.repository.BnplOrderRepository.BnplDailyUsageRow;
import com.kkpp.admin.bnpl.repository.BnplOrderRepository.RecentBnplOrderRow;
import com.kkpp.admin.bnpl.repository.InterestLedgerRepository;
import com.kkpp.admin.bnpl.repository.LoanOverdueLedgerRepository;
import com.kkpp.admin.bnpl.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.repository.CreditReviewApplicationRepository;
import com.kkpp.admin.dashboard.dto.DashboardSummaryResponse;
import com.kkpp.admin.dashboard.dto.DashboardSummaryResponse.ActionRequired;
import com.kkpp.admin.dashboard.dto.DashboardSummaryResponse.DailyBnplUsage;
import com.kkpp.admin.dashboard.dto.DashboardSummaryResponse.RecentBnplOrder;
import com.kkpp.admin.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_DAYS = 7;

    private final CreditReviewApplicationRepository creditReviewApplicationRepository;
    private final BnplOrderRepository bnplOrderRepository;
    private final InterestLedgerRepository interestLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final LoanOverdueLedgerRepository loanOverdueLedgerRepository;
    private final BnplCreditLimitRepository bnplCreditLimitRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        try {
            LocalDate today = LocalDate.now();
            YearMonth currentMonth = YearMonth.from(today);
            LocalDate startOfMonth = currentMonth.atDay(1);
            LocalDate endOfMonth = currentMonth.atEndOfMonth();
            LocalDateTime startOfMonthAt = startOfMonth.atStartOfDay();
            LocalDateTime nextMonthStartAt = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

            log.info(
                    "관리자 대시보드 요약 조회 요청: 기준일={}, 당월시작일={}, 당월종료일={}",
                    today,
                    startOfMonth,
                    endOfMonth
            );

            long pendingCreditReviewCount = creditReviewApplicationRepository.countByStatus(CreditReviewStatus.PENDING);
            BigDecimal monthlyBnplPaymentAmount = zeroIfNull(
                    bnplOrderRepository.sumBnplOrderAmountBetween(startOfMonthAt, nextMonthStartAt)
            );
            BigDecimal monthlyScheduledRepaymentAmount = zeroIfNull(
                    interestLedgerRepository.sumScheduledRepaymentThisMonth(startOfMonth, endOfMonth)
            ).add(zeroIfNull(
                    principalRepaymentLedgerRepository.sumScheduledRepaymentThisMonth(startOfMonth, endOfMonth)
            ));

            long overdueUserCount = loanOverdueLedgerRepository.countDistinctOverdueUsers();
            long activeBnplUserCount = bnplCreditLimitRepository.countCurrentActiveBnplUsers();
            BigDecimal currentOverdueRatePercent = calculateOverdueRatePercent(overdueUserCount, activeBnplUserCount);

            LocalDate trendStartDate = today.minusDays(RECENT_DAYS - 1L);
            List<DailyBnplUsage> recentSevenDaysBnplUsage = getRecentSevenDaysBnplUsage(trendStartDate, today);
            List<RecentBnplOrder> recentBnplOrders = getRecentBnplOrders();

            long outOfStockProductCount = productRepository.countOutOfStockOrSoldOutProducts();
            long overdueIssueCount = loanOverdueLedgerRepository.countByResolvedAtIsNull();

            log.info(
                    "관리자 대시보드 요약 조회 완료: pendingReviews={}, monthlyBnplAmount={}, monthlyScheduledRepayment={}, overdueRatePercent={}, overdueUsers={}, activeBnplUsers={}, outOfStockProducts={}, overdueIssues={}",
                    pendingCreditReviewCount,
                    monthlyBnplPaymentAmount,
                    monthlyScheduledRepaymentAmount,
                    currentOverdueRatePercent,
                    overdueUserCount,
                    activeBnplUserCount,
                    outOfStockProductCount,
                    overdueIssueCount
            );

            return new DashboardSummaryResponse(
                    pendingCreditReviewCount,
                    monthlyBnplPaymentAmount,
                    monthlyScheduledRepaymentAmount,
                    currentOverdueRatePercent,
                    overdueUserCount,
                    activeBnplUserCount,
                    recentSevenDaysBnplUsage,
                    recentBnplOrders,
                    new ActionRequired(
                            pendingCreditReviewCount,
                            outOfStockProductCount,
                            overdueIssueCount
                    )
            );
        } catch (BusinessException exception) {
            log.warn("관리자 대시보드 요약 조회 실패: {}", exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            log.error("관리자 대시보드 요약 조회 중 예외가 발생했습니다.", exception);
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "관리자 대시보드 요약 조회 중 오류가 발생했습니다."
            );
        }
    }

    private List<DailyBnplUsage> getRecentSevenDaysBnplUsage(LocalDate startDate, LocalDate endDate) {
        List<BnplDailyUsageRow> rows = bnplOrderRepository.findDailyBnplUsageBetween(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        );
        Map<LocalDate, BigDecimal> amountByDate = rows.stream()
                .collect(Collectors.toMap(
                        BnplDailyUsageRow::getUsageDate,
                        row -> zeroIfNull(row.getAmount()),
                        BigDecimal::add
                ));

        List<DailyBnplUsage> result = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            result.add(new DailyBnplUsage(cursor, amountByDate.getOrDefault(cursor, BigDecimal.ZERO)));
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private List<RecentBnplOrder> getRecentBnplOrders() {
        return bnplOrderRepository.findRecentBnplOrders()
                .stream()
                .map(this::toRecentBnplOrder)
                .toList();
    }

    private RecentBnplOrder toRecentBnplOrder(RecentBnplOrderRow row) {
        String firstProductName = trimToNull(row.getFirstProductName());
        long itemCount = row.getItemCount();
        return new RecentBnplOrder(
                row.getOrderPublicId(),
                row.getUserName(),
                firstProductName,
                itemCount,
                buildOrderDisplayName(firstProductName, itemCount),
                zeroIfNull(row.getAmount()),
                row.getOrderedAt()
        );
    }

    private String buildOrderDisplayName(String firstProductName, long itemCount) {
        if (firstProductName == null) {
            return "외상 주문";
        }
        if (itemCount <= 1) {
            return firstProductName;
        }
        return firstProductName + " +" + (itemCount - 1);
    }

    private BigDecimal calculateOverdueRatePercent(long overdueUserCount, long activeBnplUserCount) {
        if (activeBnplUserCount <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(overdueUserCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(activeBnplUserCount), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
