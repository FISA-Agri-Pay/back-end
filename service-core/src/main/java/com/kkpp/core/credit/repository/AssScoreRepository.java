package com.kkpp.core.credit.repository;

import com.kkpp.core.credit.domain.AssScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssScoreRepository extends JpaRepository<AssScore, Long> {

    Optional<AssScore> findByApplicationPublicId(UUID applicationPublicId);
}
