package com.kkpp.core.wallet.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "wallets", schema = "core")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false, unique = true)
    private UUID userPublicId;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(name = "deposit_bank_name", nullable = false, length = 50)
    private String depositBankName;

    @Column(name = "deposit_account_number", nullable = false, unique = true, length = 50)
    private String depositAccountNumber;

    @Column(nullable = false, length = 20)
    private String status;
}
