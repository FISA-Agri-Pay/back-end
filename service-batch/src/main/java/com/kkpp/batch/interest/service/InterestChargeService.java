package com.kkpp.batch.interest.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

import com.kkpp.batch.interest.domain.CreditLimit;
import com.kkpp.batch.interest.domain.InterestLedger;
import com.kkpp.batch.interest.repository.InterestLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterestChargeService {

    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");
    private static final int MONEY_SCALE = 2;
    private static final int DEFAULT_INTEREST_DUE_DAY = 11;
    private static final int MIN_INTEREST_DUE_DAY = 1;
    private static final int MAX_INTEREST_DUE_DAY = 28;

    private final InterestLedgerRepository interestLedgerRepository;

    // 한도 1건에 대해 월별 이자 원장 생성 여부를 판단하고, 생성할 원장을 조립한다.
    public Optional<InterestLedger> createMonthlyInterestLedger(
            CreditLimit creditLimit,
            YearMonth targetMonth,
            LocalDateTime createdAt
    ) {
        validateRequiredInput(creditLimit, targetMonth, createdAt);

        // Reader에서도 걸러지지만 서비스 단에서도 방어해 테스트와 재사용 시 안전하게 둔다.
        if (isZeroOrNegative(creditLimit.getUsedAmount())) {
            return Optional.empty();
        }

        LocalDate dueDate = targetMonth.atDay(resolveInterestDueDay(creditLimit));

        // 이자 납부 예정일이 원금 상환일 이후면 더 이상 이자 원장을 만들지 않는다.
        if (dueDate.isAfter(creditLimit.getPrincipalDueDate())) {
            return Optional.empty();
        }

        // 동일 한도와 동일 납부 예정일에 이미 생성된 원장이 있으면 멱등하게 스킵한다.
        if (interestLedgerRepository.existsByCreditLimitIdAndDueDate(creditLimit.getId(), dueDate)) {
            return Optional.empty();
        }

        BigDecimal basePrincipal = toMoney(creditLimit.getUsedAmount());
        BigDecimal interestAmount = calculateMonthlyInterest(basePrincipal, creditLimit.getInterestRate());

        return Optional.of(InterestLedger.create(
                creditLimit.getId(),
                basePrincipal,
                dueDate,
                interestAmount,
                createdAt
        ));
    }

    // interestRate는 연이율이므로 12로 나누어 월 이자를 산출한다.
    private BigDecimal calculateMonthlyInterest(BigDecimal usedAmount, BigDecimal annualInterestRate) {
        return usedAmount.multiply(defaultZero(annualInterestRate))
                .divide(MONTHS_PER_YEAR, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void validateRequiredInput(CreditLimit creditLimit, YearMonth targetMonth, LocalDateTime createdAt) {
        if (creditLimit == null) {
            throw new IllegalArgumentException("이자 원장 생성 대상 한도가 없습니다.");
        }
        if (targetMonth == null) {
            throw new IllegalArgumentException("이자 원장 생성 대상 월이 없습니다.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("이자 원장 생성 시각이 없습니다.");
        }
        if (creditLimit.getPrincipalDueDate() == null) {
            throw new IllegalStateException("credit_limits.principal_due_date가 없어 이자 원장을 생성할 수 없습니다. creditLimitId="
                    + creditLimit.getId());
        }
    }

    private int resolveInterestDueDay(CreditLimit creditLimit) {
        Integer interestDueDay = creditLimit.getInterestDueDay();
        if (interestDueDay == null) {
            return DEFAULT_INTEREST_DUE_DAY;
        }
        if (interestDueDay < MIN_INTEREST_DUE_DAY || interestDueDay > MAX_INTEREST_DUE_DAY) {
            throw new IllegalStateException("credit_limits.interest_due_day는 1~28 사이여야 합니다. creditLimitId="
                    + creditLimit.getId() + ", interestDueDay=" + interestDueDay);
        }
        return interestDueDay;
    }

    // 원장 테이블의 NUMERIC(15,2)에 맞춰 금액 스케일을 통일한다.
    private BigDecimal toMoney(BigDecimal value) {
        return defaultZero(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isZeroOrNegative(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0;
    }
}
