package com.kkpp.batch.overdue.repository;

import com.kkpp.batch.principal.repayment.domain.CreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OverdueCreditLimitRepository extends JpaRepository<CreditLimit, Long> {
}
