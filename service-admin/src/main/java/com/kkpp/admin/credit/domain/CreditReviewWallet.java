package com.kkpp.admin.credit.domain;

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

@Getter
@Entity
@Table(name = "wallets", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditReviewWallet extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_DEPOSIT_BANK_NAME = "local-bank";
    // 실제 계좌 연동 전까지는 사용자 publicId 기반의 시스템 계좌번호를 사용해 사용자 간 중복을 피한다.
    private static final String ACCOUNT_NUMBER_PREFIX = "KKPP-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false, unique = true)
    private UUID userPublicId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(name = "deposit_bank_name", nullable = false, length = 50)
    private String depositBankName;

    @Column(name = "deposit_account_number", nullable = false, unique = true, length = 50)
    private String depositAccountNumber;

    @Column(nullable = false, length = 20)
    private String status;

    // 한도 승인 시 지갑이 없는 사용자에게 발급되는 기본 지갑이다.
    public static CreditReviewWallet issue(UUID userPublicId) {
        if (userPublicId == null) {
            throw new IllegalArgumentException("User public id is required.");
        }

        CreditReviewWallet wallet = new CreditReviewWallet();
        wallet.publicId = UUID.randomUUID();
        wallet.userPublicId = userPublicId;
        wallet.balance = BigDecimal.ZERO;
        wallet.depositBankName = DEFAULT_DEPOSIT_BANK_NAME;
        wallet.depositAccountNumber = ACCOUNT_NUMBER_PREFIX + userPublicId.toString().replace("-", "");
        wallet.status = STATUS_ACTIVE;
        return wallet;
    }
}
