package com.kkpp.batch.overdue.repository;

import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.principal.repayment.domain.CreditLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OverdueCreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    Optional<CreditLimit> findByPublicId(UUID publicId);
}
