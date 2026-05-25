package com.kkpp.core.credit.repository;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLimitApplicationRepository extends JpaRepository<CreditLimitApplication, Long> {

    boolean existsByUserIdAndStatus(Long userId, ApplicationStatus status);
}
