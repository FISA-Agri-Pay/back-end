package com.kkpp.admin.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kkpp.admin.bnpl.repository.BnplCreditLimitRepository;
import com.kkpp.admin.bnpl.repository.BnplOrderRepository;
import com.kkpp.admin.bnpl.repository.InterestLedgerRepository;
import com.kkpp.admin.bnpl.repository.LoanOverdueLedgerRepository;
import com.kkpp.admin.bnpl.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.repository.CreditReviewApplicationRepository;
import com.kkpp.admin.dashboard.dto.DashboardSummaryResponse;
import com.kkpp.admin.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CreditReviewApplicationRepository creditReviewApplicationRepository;

    @Mock
    private BnplOrderRepository bnplOrderRepository;

    @Mock
    private InterestLedgerRepository interestLedgerRepository;

    @Mock
    private PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;

    @Mock
    private LoanOverdueLedgerRepository loanOverdueLedgerRepository;

    @Mock
    private BnplCreditLimitRepository bnplCreditLimitRepository;

    @Mock
    private ProductRepository productRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                creditReviewApplicationRepository,
                bnplOrderRepository,
                interestLedgerRepository,
                principalRepaymentLedgerRepository,
                loanOverdueLedgerRepository,
                bnplCreditLimitRepository,
                productRepository
        );
    }

    @Test
    void getSummaryAggregatesKpisAndFillsRecentSevenDays() {
        BnplOrderRepository.BnplDailyUsageRow dailyUsage = dailyUsage(LocalDate.now(), new BigDecimal("70000"));
        BnplOrderRepository.RecentBnplOrderRow recentOrder = recentOrder("비료", 2);
        when(creditReviewApplicationRepository.countByStatus(CreditReviewStatus.PENDING)).thenReturn(3L);
        when(bnplOrderRepository.sumBnplOrderAmountBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("100000"));
        when(interestLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("10000"));
        when(principalRepaymentLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("90000"));
        when(loanOverdueLedgerRepository.countDistinctOverdueUsers()).thenReturn(2L);
        when(bnplCreditLimitRepository.countCurrentActiveBnplUsers()).thenReturn(10L);
        when(productRepository.countOutOfStockOrSoldOutProducts()).thenReturn(4L);
        when(loanOverdueLedgerRepository.countByResolvedAtIsNull()).thenReturn(5L);
        when(bnplOrderRepository.findDailyBnplUsageBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(dailyUsage));
        when(bnplOrderRepository.findRecentBnplOrders()).thenReturn(List.of(recentOrder));

        DashboardSummaryResponse response = dashboardService.getSummary();

        assertThat(response.pendingCreditReviewCount()).isEqualTo(3);
        assertThat(response.monthlyScheduledRepaymentAmount()).isEqualByComparingTo("100000");
        assertThat(response.currentOverdueRatePercent()).isEqualByComparingTo("20.00");
        assertThat(response.recentSevenDaysBnplUsage()).hasSize(7);
        assertThat(response.recentBnplOrders().getFirst().orderDisplayName()).isEqualTo("비료 +1");
        assertThat(response.actionRequired().outOfStockProductCount()).isEqualTo(4);
    }

    @Test
    void getSummaryNormalizesNullAmountsAndZeroActiveUsers() {
        BnplOrderRepository.RecentBnplOrderRow recentOrder = recentOrder(" ", 0);
        when(creditReviewApplicationRepository.countByStatus(CreditReviewStatus.PENDING)).thenReturn(0L);
        when(bnplOrderRepository.sumBnplOrderAmountBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(null);
        when(interestLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(null);
        when(principalRepaymentLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(null);
        when(loanOverdueLedgerRepository.countDistinctOverdueUsers()).thenReturn(1L);
        when(bnplCreditLimitRepository.countCurrentActiveBnplUsers()).thenReturn(0L);
        when(bnplOrderRepository.findDailyBnplUsageBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(bnplOrderRepository.findRecentBnplOrders()).thenReturn(List.of(recentOrder));

        DashboardSummaryResponse response = dashboardService.getSummary();

        assertThat(response.monthlyBnplPaymentAmount()).isEqualByComparingTo("0");
        assertThat(response.monthlyScheduledRepaymentAmount()).isEqualByComparingTo("0");
        assertThat(response.currentOverdueRatePercent()).isEqualByComparingTo("0.00");
        assertThat(response.recentBnplOrders().getFirst().orderDisplayName()).isEqualTo("외상 주문");
    }

    @Test
    void getSummaryWrapsUnexpectedRuntimeException() {
        when(creditReviewApplicationRepository.countByStatus(CreditReviewStatus.PENDING))
                .thenThrow(new IllegalStateException("db error"));

        assertThatThrownBy(() -> dashboardService.getSummary())
                .isInstanceOf(BusinessException.class);
    }

    private BnplOrderRepository.BnplDailyUsageRow dailyUsage(LocalDate date, BigDecimal amount) {
        BnplOrderRepository.BnplDailyUsageRow row = mock(BnplOrderRepository.BnplDailyUsageRow.class);
        when(row.getUsageDate()).thenReturn(date);
        when(row.getAmount()).thenReturn(amount);
        return row;
    }

    private BnplOrderRepository.RecentBnplOrderRow recentOrder(String firstProductName, long itemCount) {
        BnplOrderRepository.RecentBnplOrderRow row = mock(BnplOrderRepository.RecentBnplOrderRow.class);
        when(row.getOrderPublicId()).thenReturn(UUID.randomUUID());
        when(row.getUserName()).thenReturn("홍길동");
        when(row.getFirstProductName()).thenReturn(firstProductName);
        when(row.getItemCount()).thenReturn(itemCount);
        when(row.getAmount()).thenReturn(new BigDecimal("50000"));
        when(row.getOrderedAt()).thenReturn(LocalDateTime.of(2026, 6, 13, 10, 0));
        return row;
    }
}
