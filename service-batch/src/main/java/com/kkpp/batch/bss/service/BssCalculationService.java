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

    private static final BigDecimal RATE_95 = new BigDecimal("0.95");
    private static final BigDecimal RATE_90 = new BigDecimal("0.90");
    private static final BigDecimal RATE_80 = new BigDecimal("0.80");
    private static final BigDecimal RATE_60 = new BigDecimal("0.60");

    private final InterestLedgerRepository interestLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final LoanOverdueLedgerRepository loanOverdueLedgerRepository;

    // BSS는 한도 승인 이후의 행동 점수이므로 ASS 산식을 다시 포함하지 않는다.
    // 이자/원금 납부, 연체 이력, 한도 사용률만 월별 행동 데이터로 계산한다.
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
                source.userPublicId(),
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

    // 한도별 월별 원장 데이터를 모아 BSS 계산 입력값으로 만든다.
    // 연체는 해당 월에 발생했거나, 해당 월 동안 활성 상태였거나, 해당 월에 해소된 이력을 포함한다.
    private BssCalculationSource buildSource(CreditLimit creditLimit, YearMonth period) {
        LocalDate startDate = period.atDay(1);
        LocalDate endDateExclusive = period.plusMonths(1).atDay(1);

        List<InterestLedger> interests =
                interestLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                        creditLimit.getPublicId(),
                        startDate,
                        endDateExclusive
                );
        List<PrincipalRepaymentLedger> principals =
                principalRepaymentLedgerRepository.findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
                        creditLimit.getPublicId(),
                        startDate,
                        endDateExclusive
                );
        List<LoanOverdueLedger> overdues = loanOverdueLedgerRepository.findMonthlyOverdues(
                creditLimit.getPublicId(),
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
                creditLimit.getUserPublicId(),
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

    // 이자 청구가 없으면 아직 평가할 행동이 없는 상태이므로 불량으로 보지 않고 중립 점수 15점을 준다.
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

    // 원금 상환 예정이 없으면 아직 원금 상환 행동을 평가할 수 없으므로 중립 점수 9점을 준다.
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

    // 미해결 연체는 현재 남아 있는 가장 강한 위험 신호이므로 연체 점수를 0점 처리한다.
    // 해소된 연체도 삭제하지 않고 이력으로 남기므로 건수와 최대 연체일 기준 감점에 포함한다.
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

    // 한도 사용률은 상환 능력 자체가 아니라 보조 리스크 지표이므로 20점 범위에서만 반영한다.
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
