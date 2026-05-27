package com.kkpp.batch.interest.repository;

import java.time.LocalDate;

import com.kkpp.batch.interest.domain.InterestLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("interestChargeLedgerRepository")
public interface InterestLedgerRepository extends JpaRepository<InterestLedger, Long> {

    // 같은 한도와 같은 납부 예정일의 원장 중복 생성을 막기 위한 존재 여부 조회다.
    boolean existsByCreditLimitIdAndDueDate(Long creditLimitId, LocalDate dueDate);
}
