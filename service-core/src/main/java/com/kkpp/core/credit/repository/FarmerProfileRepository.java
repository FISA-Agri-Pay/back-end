package com.kkpp.core.credit.repository;

import com.kkpp.core.credit.domain.FarmerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, Long> {

    Optional<FarmerProfile> findByUserPublicId(UUID userPublicId);
}
