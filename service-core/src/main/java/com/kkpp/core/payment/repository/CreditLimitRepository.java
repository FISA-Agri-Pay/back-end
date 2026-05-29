package com.kkpp.core.payment.repository;

import com.kkpp.core.payment.domain.CreditLimit;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CreditLimit> findFirstByUserPublicIdAndStatusOrderByIdDesc(UUID userPublicId, String status);
}
