package com.kkpp.batch.bss.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kkpp.batch.bss.domain.InterestLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {

    List<InterestLedger> findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
            UUID creditLimitPublicId,
            LocalDate startDate,
            LocalDate endDateExclusive
    );
}
