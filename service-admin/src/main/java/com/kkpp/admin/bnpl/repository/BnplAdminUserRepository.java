package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplAdminUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BnplAdminUserRepository extends JpaRepository<BnplAdminUser, Long> {

    Optional<BnplAdminUser> findByPublicId(UUID publicId);
}
