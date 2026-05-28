package com.kkpp.batch.overdue.repository;

import java.util.Optional;

import com.kkpp.batch.overdue.domain.LoanOverdueLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OverdueLoanOverdueLedgerRepository extends JpaRepository<LoanOverdueLedger, Long> {

    Optional<LoanOverdueLedger> findByInterestLedgerIdAndResolvedAtIsNull(Long interestLedgerId);

    Optional<LoanOverdueLedger> findByPrincipalRepaymentLedgerIdAndResolvedAtIsNull(Long principalRepaymentLedgerId);
}
