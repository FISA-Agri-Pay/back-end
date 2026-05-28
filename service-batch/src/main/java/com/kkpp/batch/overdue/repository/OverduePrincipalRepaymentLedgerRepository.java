package com.kkpp.batch.overdue.repository;

import java.util.Optional;

import com.kkpp.batch.principal.repayment.domain.PrincipalRepaymentLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OverduePrincipalRepaymentLedgerRepository extends JpaRepository<PrincipalRepaymentLedger, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PrincipalAutoPaymentLedger p WHERE p.id = :id")
    Optional<PrincipalRepaymentLedger> findByIdForUpdate(@Param("id") Long id);
}
