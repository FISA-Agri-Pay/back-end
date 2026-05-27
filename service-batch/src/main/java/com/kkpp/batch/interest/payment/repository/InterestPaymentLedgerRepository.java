package com.kkpp.batch.interest.payment.repository;

import java.util.Optional;

import com.kkpp.batch.interest.domain.InterestLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository("interestPaymentLedgerRepository")
public interface InterestPaymentLedgerRepository extends JpaRepository<InterestLedger, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InterestChargeLedger i WHERE i.id = :id")
    Optional<InterestLedger> findByIdForUpdate(@Param("id") Long id);
}
