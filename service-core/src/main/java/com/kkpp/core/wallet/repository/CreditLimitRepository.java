package com.kkpp.core.wallet.repository;

import com.kkpp.core.wallet.domain.CreditLimit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    Optional<CreditLimit> findFirstByUserPublicIdAndStatusOrderByCreatedAtDesc(UUID userPublicId, String status);
}
