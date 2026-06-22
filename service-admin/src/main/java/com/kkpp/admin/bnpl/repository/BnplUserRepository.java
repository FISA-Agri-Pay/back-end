package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BnplUserRepository extends JpaRepository<BnplUser, Long> {

    Optional<BnplUser> findByPublicId(UUID publicId);
}
