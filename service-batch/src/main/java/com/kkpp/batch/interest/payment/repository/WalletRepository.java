package com.kkpp.batch.interest.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.kkpp.batch.interest.payment.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM InterestPaymentWallet w WHERE w.userPublicId = :userPublicId")
    Optional<Wallet> findByUserPublicIdForUpdate(@Param("userPublicId") UUID userPublicId);
}
