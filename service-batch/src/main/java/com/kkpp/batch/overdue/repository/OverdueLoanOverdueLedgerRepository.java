package com.kkpp.batch.overdue.repository;

import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.overdue.domain.LoanOverdueLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OverdueLoanOverdueLedgerRepository extends JpaRepository<LoanOverdueLedger, Long> {

    Optional<LoanOverdueLedger> findByInterestLedgerPublicIdAndResolvedAtIsNull(UUID interestLedgerPublicId);

    Optional<LoanOverdueLedger> findByPrincipalRepaymentPublicIdAndResolvedAtIsNull(UUID principalRepaymentPublicId);
}
