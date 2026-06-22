package com.kkpp.core.wallet.repository;

import com.kkpp.core.wallet.domain.Wallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserPublicId(UUID userPublicId);
}
