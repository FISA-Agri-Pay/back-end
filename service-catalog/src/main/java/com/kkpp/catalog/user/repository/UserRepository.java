package com.kkpp.catalog.user.repository;

import com.kkpp.catalog.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
