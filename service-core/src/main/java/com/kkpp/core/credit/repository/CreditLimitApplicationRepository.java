package com.kkpp.core.credit.repository;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLimitApplicationRepository extends JpaRepository<CreditLimitApplication, Long> {

    boolean existsByUserPublicIdAndStatus(UUID userPublicId, ApplicationStatus status);
}
