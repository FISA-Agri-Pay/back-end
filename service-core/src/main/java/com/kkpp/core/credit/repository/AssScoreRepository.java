package com.kkpp.core.credit.repository;

import com.kkpp.core.credit.domain.AssScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssScoreRepository extends JpaRepository<AssScore, Long> {

    Optional<AssScore> findByApplication_Id(Long applicationId);
}
