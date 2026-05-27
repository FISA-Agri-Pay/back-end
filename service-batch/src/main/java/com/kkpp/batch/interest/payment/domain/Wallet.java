package com.kkpp.batch.interest.payment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "InterestPaymentWallet")
@Table(name = "wallets", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public boolean hasBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) > 0;
    }

    // 실제 자동 상환 금액만큼 지갑 잔액을 차감한다.
    public void withdraw(BigDecimal amount, LocalDateTime updatedAt) {
        BigDecimal moneyAmount = toMoney(amount);
        if (moneyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("지갑 차감 금액은 0보다 커야 합니다.");
        }
        if (balance == null || balance.compareTo(moneyAmount) < 0) {
            throw new IllegalStateException("지갑 잔액보다 큰 금액은 차감할 수 없습니다. walletId=" + id);
        }

        balance = toMoney(balance.subtract(moneyAmount));
        this.updatedAt = updatedAt;
    }

    private BigDecimal toMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
