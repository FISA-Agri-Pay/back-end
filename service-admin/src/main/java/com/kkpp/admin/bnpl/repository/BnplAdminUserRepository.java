package com.kkpp.admin.bnpl.repository;

import com.kkpp.admin.bnpl.domain.BnplAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BnplAdminUserRepository extends JpaRepository<BnplAdminUser, Long> {
}
