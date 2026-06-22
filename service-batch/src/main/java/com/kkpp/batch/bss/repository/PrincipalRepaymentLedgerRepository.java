package com.kkpp.batch.bss.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.kkpp.batch.bss.domain.PrincipalRepaymentLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrincipalRepaymentLedgerRepository extends JpaRepository<PrincipalRepaymentLedger, Long> {

    List<PrincipalRepaymentLedger> findAllByCreditLimitPublicIdAndDueDateGreaterThanEqualAndDueDateLessThan(
            UUID creditLimitPublicId,
            LocalDate startDate,
            LocalDate endDateExclusive
    );
}
