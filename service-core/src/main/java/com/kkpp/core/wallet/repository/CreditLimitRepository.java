package com.kkpp.core.wallet.repository;

import com.kkpp.core.wallet.domain.CreditLimit;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditLimitRepository extends JpaRepository<CreditLimit, Long> {

    @Query("""
            SELECT c
            FROM WalletCreditLimit c
            WHERE c.userPublicId = :userPublicId
              AND c.status = :status
              AND (c.expiresAt IS NULL OR c.expiresAt >= :today)
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    Optional<CreditLimit> findLatestUsableActiveLimit(
            @Param("userPublicId") UUID userPublicId,
            @Param("status") String status,
            @Param("today") LocalDate today
    );
}
