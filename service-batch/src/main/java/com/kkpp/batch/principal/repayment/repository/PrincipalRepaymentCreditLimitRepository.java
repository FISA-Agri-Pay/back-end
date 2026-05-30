package com.kkpp.batch.principal.repayment.repository;

import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.principal.repayment.domain.CreditLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository("principalAutoPaymentCreditLimitRepository")
public interface PrincipalRepaymentCreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM PrincipalRepaymentCreditLimit c WHERE c.publicId = :publicId")
    Optional<CreditLimit> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
