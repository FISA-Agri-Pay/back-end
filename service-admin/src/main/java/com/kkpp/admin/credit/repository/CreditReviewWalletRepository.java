package com.kkpp.admin.credit.repository;

import com.kkpp.admin.credit.domain.CreditReviewWallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditReviewWalletRepository extends JpaRepository<CreditReviewWallet, Long> {

    boolean existsByUserPublicId(UUID userPublicId);

    Optional<CreditReviewWallet> findByUserPublicId(UUID userPublicId);
}
