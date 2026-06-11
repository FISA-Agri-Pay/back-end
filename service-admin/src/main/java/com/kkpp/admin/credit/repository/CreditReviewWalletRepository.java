package com.kkpp.admin.credit.repository;

import com.kkpp.admin.credit.domain.CreditReviewWallet;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditReviewWalletRepository extends JpaRepository<CreditReviewWallet, Long> {

    boolean existsByUserPublicId(UUID userPublicId);

    Optional<CreditReviewWallet> findByUserPublicId(UUID userPublicId);

    @Modifying
    @Query(value = """
            INSERT INTO core.wallets (
                public_id,
                user_public_id,
                balance,
                deposit_bank_name,
                deposit_account_number,
                status
            )
            VALUES (
                :publicId,
                :userPublicId,
                :balance,
                :depositBankName,
                :depositAccountNumber,
                :status
            )
            ON CONFLICT (user_public_id) DO NOTHING
            """, nativeQuery = true)
    int insertWalletIfAbsent(
            @Param("publicId") UUID publicId,
            @Param("userPublicId") UUID userPublicId,
            @Param("balance") BigDecimal balance,
            @Param("depositBankName") String depositBankName,
            @Param("depositAccountNumber") String depositAccountNumber,
            @Param("status") String status
    );
}
