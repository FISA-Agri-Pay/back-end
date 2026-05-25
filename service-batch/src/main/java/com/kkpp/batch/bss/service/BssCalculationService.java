package com.kkpp.batch.bss.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import com.kkpp.batch.bss.domain.CreditLimit;
import com.kkpp.batch.bss.domain.InterestLedger;
import com.kkpp.batch.bss.domain.LoanOverdueLedger;
import com.kkpp.batch.bss.domain.PeriodType;
import com.kkpp.batch.bss.domain.PrincipalRepaymentLedger;
import com.kkpp.batch.bss.dto.BssCalculationResult;
import com.kkpp.batch.bss.dto.BssCalculationSource;
import com.kkpp.batch.bss.repository.InterestLedgerRepository;
import com.kkpp.batch.bss.repository.LoanOverdueLedgerRepository;
import com.kkpp.batch.bss.repository.PrincipalRepaymentLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BssCalculationService {

    // BSS 기준 점수표에서 사용하는 납부율/사용률 경계값이다.
    private static final BigDecimal RATE_95 = new BigDecimal("0.95");
    private static final BigDecimal RATE_90 = new BigDecimal("0.90");
    private static final BigDecimal RATE_80 = new BigDecimal("0.80");
    private static final BigDecimal RATE_60 = new BigDecimal("0.60");

    private final InterestLedgerRepository interestLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final LoanOverdueLedgerRepository loanOverdueLedgerRepository;

    // ASS는 BSS 계산에 포함하지 않는다. 승인 이후 행동 데이터만 조립해서 월별 점수를 계산한다.
    public BssCalculationResult calculate(CreditLimit creditLimit, YearMonth period, LocalDateTime calculatedAt) {
        BssCalculationSource source = buildSource(creditLimit, period);

        int repaymentScore = calculateInterestPaymentScore(
                source.interestPaidAmount(),
                source.interestBilledAmount()
        ) + calculatePrincipalPaymentScore(
                source.principalPaidAmount(),
                source.principalBilledAmount()
        );
        int overdueScore = calculateOverdueScore(
                source.hasUnresolvedOverdue(),
                source.overdueCount(),
                source.maxOverdueDays()
        );
        int usageScore = calculateUsageScore(source.usedAmount(), source.totalLimit());
        int totalScore = repaymentScore + overdueScore + usageScore;

        return new BssCalculationResult(
                source.userId(),
                repaymentScore,
                overdueScore,
                usageScore,
                totalScore,
                totalScore,
                PeriodType.MONTHLY,
                period.getYear(),
                period.getMonthValue(),
                calculatedAt
        );
    }

    // 한도 하나에 대해 해당 월의 이자/원금/연체 이력을 모아 계산 입력값으로 만든다.
    private BssCalculationSource buildSource(CreditLimit creditLimit, YearMonth period) {
        LocalDate startDate = period.atDay(1);
        LocalDate endDateExclusive = period.plusMonths(1).atDay(1);

        List<InterestLedger> interests =
                interestLedgerRepository.findAllByCreditLimitIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                        creditLimit.getId(),
                        startDate,
                        endDateExclusive
                );
        List<PrincipalRepaymentLedger> principals =
                principalRepaymentLedgerRepository.findAllByCreditLimitIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                        creditLimit.getId(),
                        startDate,
                        endDateExclusive
                );
        List<LoanOverdueLedger> overdues = loanOverdueLedgerRepository.findMonthlyOverdues(
                creditLimit.getId(),
                startDate.atStartOfDay(),
                endDateExclusive.atStartOfDay()
        );

        BigDecimal interestBilledAmount = sum(interests.stream()
                .map(InterestLedger::getInterestAmount)
                .toList());
        BigDecimal interestPaidAmount = sum(interests.stream()
                .map(InterestLedger::getAmountPaid)
                .toList());
        BigDecimal principalBilledAmount = sum(principals.stream()
                .map(PrincipalRepaymentLedger::getPrincipalAmount)
                .toList());
        BigDecimal principalPaidAmount = sum(principals.stream()
                .map(PrincipalRepaymentLedger::getAmountPaid)
                .toList());

        boolean hasUnresolvedOverdue = overdues.stream().anyMatch(overdue -> overdue.getResolvedAt() == null);
        int maxOverdueDays = overdues.stream()
                .map(LoanOverdueLedger::getOverdueDays)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        return new BssCalculationSource(
                creditLimit.getUserId(),
                creditLimit.getTotalLimit(),
                creditLimit.getUsedAmount(),
                interestBilledAmount,
                interestPaidAmount,
                principalBilledAmount,
                principalPaidAmount,
                overdues.size(),
                maxOverdueDays,
                hasUnresolvedOverdue
        );
    }

    // 청구 이력이 없으면 불량 행동으로 보지 않고 중립 점수 15점을 부여한다.
    private int calculateInterestPaymentScore(BigDecimal paidAmount, BigDecimal billedAmount) {
        if (isZeroOrNegative(billedAmount)) {
            return 15;
        }

        BigDecimal paymentRate = calculateRate(paidAmount, billedAmount);
        if (paymentRate.compareTo(RATE_95) >= 0) {
            return 25;
        }
        if (paymentRate.compareTo(RATE_80) >= 0) {
            return 18;
        }
        if (paymentRate.compareTo(RATE_60) >= 0) {
            return 10;
        }
        return 5;
    }

    // 상환 이력이 없으면 불량 행동으로 보지 않고 중립 점수 9점을 부여한다.
    private int calculatePrincipalPaymentScore(BigDecimal paidAmount, BigDecimal billedAmount) {
        if (isZeroOrNegative(billedAmount)) {
            return 9;
        }

        BigDecimal paymentRate = calculateRate(paidAmount, billedAmount);
        if (paymentRate.compareTo(RATE_95) >= 0) {
            return 15;
        }
        if (paymentRate.compareTo(RATE_80) >= 0) {
            return 10;
        }
        if (paymentRate.compareTo(RATE_60) >= 0) {
            return 6;
        }
        return 3;
    }

    // 미해결 연체는 최우선 위험 신호이므로 다른 이력보다 먼저 0점 처리한다.
    private int calculateOverdueScore(boolean hasUnresolvedOverdue, long overdueCount, int maxOverdueDays) {
        if (hasUnresolvedOverdue) {
            return 0;
        }
        if (overdueCount == 0) {
            return 40;
        }
        if (overdueCount >= 3 || maxOverdueDays > 30) {
            return 10;
        }
        if (overdueCount <= 2 && maxOverdueDays <= 7) {
            return 30;
        }
        if (overdueCount <= 2 && maxOverdueDays <= 30) {
            return 20;
        }
        return 10;
    }

    // 한도 사용률은 보조 리스크 지표라 90% 이하까지는 감점하지 않는다.
    private int calculateUsageScore(BigDecimal usedAmount, BigDecimal totalLimit) {
        if (isZeroOrNegative(totalLimit)) {
            return 0;
        }

        BigDecimal usageRate = defaultZero(usedAmount).divide(totalLimit, 4, RoundingMode.HALF_UP);
        if (usageRate.compareTo(RATE_90) <= 0) {
            return 20;
        }
        if (usageRate.compareTo(BigDecimal.ONE) <= 0) {
            return 15;
        }
        return 0;
    }

    private BigDecimal calculateRate(BigDecimal paidAmount, BigDecimal billedAmount) {
        return defaultZero(paidAmount).divide(billedAmount, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .map(this::defaultZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isZeroOrNegative(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0;
    }
}
