package com.kkpp.batch.bss.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.kkpp.batch.bss.domain.CreditLimit;
import com.kkpp.batch.bss.dto.BssCalculationResult;
import com.kkpp.batch.bss.repository.InterestLedgerRepository;
import com.kkpp.batch.bss.repository.LoanOverdueLedgerRepository;
import com.kkpp.batch.bss.repository.PrincipalRepaymentLedgerRepository;
import org.junit.jupiter.api.Test;

class BssCalculationServiceTest {

    private final BssCalculationService service = new BssCalculationService(
            mockInterestRepository(),
            mockPrincipalRepository(),
            mockOverdueRepository()
    );

    @Test
    void calculateGivesNeutralRepaymentScoresWhenNoLedgerExists() throws Exception {
        BssCalculationResult result = service.calculate(
                creditLimit(1L, 1L, new BigDecimal("1000000"), new BigDecimal("900000")),
                YearMonth.of(2026, 4),
                java.time.LocalDateTime.of(2026, 5, 1, 1, 0)
        );

        assertThat(result.repaymentScore()).isEqualTo(24);
        assertThat(result.overdueScore()).isEqualTo(40);
        assertThat(result.usageScore()).isEqualTo(20);
        assertThat(result.totalScore()).isEqualTo(84);
    }

    private InterestLedgerRepository mockInterestRepository() {
        return (InterestLedgerRepository) java.lang.reflect.Proxy.newProxyInstance(
                InterestLedgerRepository.class.getClassLoader(),
                new Class<?>[]{InterestLedgerRepository.class},
                (proxy, method, args) -> method.getName().startsWith("findAllBy") ? List.of() : null
        );
    }

    private PrincipalRepaymentLedgerRepository mockPrincipalRepository() {
        return (PrincipalRepaymentLedgerRepository) java.lang.reflect.Proxy.newProxyInstance(
                PrincipalRepaymentLedgerRepository.class.getClassLoader(),
                new Class<?>[]{PrincipalRepaymentLedgerRepository.class},
                (proxy, method, args) -> method.getName().startsWith("findAllBy") ? List.of() : null
        );
    }

    private LoanOverdueLedgerRepository mockOverdueRepository() {
        return (LoanOverdueLedgerRepository) java.lang.reflect.Proxy.newProxyInstance(
                LoanOverdueLedgerRepository.class.getClassLoader(),
                new Class<?>[]{LoanOverdueLedgerRepository.class},
                (proxy, method, args) -> method.getName().equals("findMonthlyOverdues") ? List.of() : null
        );
    }

    private CreditLimit creditLimit(Long id, Long userId, BigDecimal totalLimit, BigDecimal usedAmount)
            throws Exception {
        Constructor<CreditLimit> constructor = CreditLimit.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CreditLimit creditLimit = constructor.newInstance();
        setField(creditLimit, "id", id);
        setField(creditLimit, "userId", userId);
        setField(creditLimit, "totalLimit", totalLimit);
        setField(creditLimit, "usedAmount", usedAmount);
        setField(creditLimit, "status", "ACTIVE");
        return creditLimit;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
