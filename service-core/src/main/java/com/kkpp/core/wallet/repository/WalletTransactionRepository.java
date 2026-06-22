package com.kkpp.core.wallet.repository;

import com.kkpp.core.wallet.domain.WalletTransaction;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(
            UUID walletPublicId,
            Collection<String> transactionTypes
    );
}
