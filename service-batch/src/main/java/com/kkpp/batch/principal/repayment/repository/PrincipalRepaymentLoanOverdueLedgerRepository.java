package com.kkpp.batch.principal.repayment.repository;

import java.util.List;

import com.kkpp.batch.principal.repayment.domain.LoanOverdueLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("principalRepaymentLoanOverdueLedgerRepository")
public interface PrincipalRepaymentLoanOverdueLedgerRepository extends JpaRepository<LoanOverdueLedger, Long> {

    List<LoanOverdueLedger> findAllByPrincipalRepaymentLedgerIdAndResolvedAtIsNull(Long principalRepaymentLedgerId);
}
