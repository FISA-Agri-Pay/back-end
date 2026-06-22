package com.kkpp.batch.interest.payment.repository;

import com.kkpp.batch.interest.payment.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
}
