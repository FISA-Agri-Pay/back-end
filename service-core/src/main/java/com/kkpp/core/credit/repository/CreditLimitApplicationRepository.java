package com.kkpp.core.credit.repository;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLimitApplicationRepository extends JpaRepository<CreditLimitApplication, Long> {

    boolean existsByUserPublicIdAndStatusIn(UUID userPublicId, Collection<ApplicationStatus> statuses);

    Optional<CreditLimitApplication> findTopByUserPublicIdOrderByAppliedAtDesc(UUID userPublicId);
}
