package com.kkpp.batch.bss.repository;

import java.time.LocalDate;
import java.util.List;

import com.kkpp.batch.bss.domain.PrincipalRepaymentLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepaymentLedgerRepository extends JpaRepository<PrincipalRepaymentLedger, Long> {

    List<PrincipalRepaymentLedger> findAllByCreditLimitIdAndDueDateGreaterThanEqualAndDueDateLessThan(
            Long creditLimitId,
            LocalDate startDate,
            LocalDate endDateExclusive
    );
}
