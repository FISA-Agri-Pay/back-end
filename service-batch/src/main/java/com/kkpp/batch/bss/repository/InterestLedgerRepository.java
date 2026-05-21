package com.kkpp.batch.bss.repository;

import java.time.LocalDate;
import java.util.List;

import com.kkpp.batch.bss.domain.InterestLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {

    List<InterestLedger> findAllByCreditLimitIdAndDueDateGreaterThanEqualAndDueDateLessThan(
            Long creditLimitId,
            LocalDate startDate,
            LocalDate endDateExclusive
    );
}
