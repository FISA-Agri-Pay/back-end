package com.kkpp.batch.interest.payment.repository;

import java.util.List;
import java.util.UUID;

import com.kkpp.batch.interest.payment.domain.LoanOverdueLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("interestPaymentLoanOverdueLedgerRepository")
public interface LoanOverdueLedgerRepository extends JpaRepository<LoanOverdueLedger, Long> {

    List<LoanOverdueLedger> findAllByInterestLedgerPublicIdAndResolvedAtIsNull(UUID interestLedgerPublicId);
}
