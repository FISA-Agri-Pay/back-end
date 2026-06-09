package com.kkpp.core.testsupport;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.user.domain.User;
import com.kkpp.core.wallet.domain.CreditLimit;
import com.kkpp.core.wallet.domain.InterestLedger;
import com.kkpp.core.wallet.domain.PrincipalRepaymentLedger;
import com.kkpp.core.wallet.domain.Wallet;
import com.kkpp.core.wallet.domain.WalletTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestEntityFactory {

    private TestEntityFactory() {
    }

    public static User user(Long id, UUID publicId, String name) {
        User user = instantiate(User.class);
        set(user, "id", id);
        set(user, "publicId", publicId);
        set(user, "name", name);
        set(user, "phone", "010-0000-0000");
        set(user, "residentIdHash", "hash");
        set(user, "address", "경기도 안성시");
        set(user, "addressDetail", "101동");
        set(user, "zipCode", "17500");
        set(user, "status", "ACTIVE");
        return user;
    }

    public static FarmerProfile farmerProfile(BigDecimal fieldAreaM2, CropType cropType, Boolean hasInsurance) {
        return FarmerProfile.create(
                UUID.randomUUID(),
                "경기도 안성시",
                "101동",
                "17500",
                fieldAreaM2,
                cropType,
                hasInsurance
        );
    }

    public static Wallet wallet(UUID publicId, UUID userPublicId, BigDecimal balance) {
        Wallet wallet = instantiate(Wallet.class);
        set(wallet, "publicId", publicId);
        set(wallet, "userPublicId", userPublicId);
        set(wallet, "balance", balance);
        set(wallet, "depositBankName", "우리은행");
        set(wallet, "depositAccountNumber", "352-0000-0000-00");
        set(wallet, "status", "ACTIVE");
        return wallet;
    }

    public static CreditLimit creditLimit(
            UUID publicId,
            UUID userPublicId,
            BigDecimal totalLimit,
            BigDecimal usedAmount,
            LocalDate principalDueDate
    ) {
        CreditLimit creditLimit = instantiate(CreditLimit.class);
        set(creditLimit, "publicId", publicId);
        set(creditLimit, "userPublicId", userPublicId);
        set(creditLimit, "applicationPublicId", UUID.fromString("22222222-2222-4222-8222-222222222222"));
        set(creditLimit, "cropTypeSnapshot", CropType.RICE.name());
        set(creditLimit, "totalLimit", totalLimit);
        set(creditLimit, "usedAmount", usedAmount);
        set(creditLimit, "interestRate", new BigDecimal("0.012"));
        set(creditLimit, "interestDueDay", 11);
        set(creditLimit, "principalDueDate", principalDueDate);
        set(creditLimit, "expiresAt", LocalDate.now().plusMonths(6));
        set(creditLimit, "status", CreditLimit.STATUS_ACTIVE);
        return creditLimit;
    }

    public static InterestLedger interestLedger(
            UUID creditLimitPublicId,
            LocalDate dueDate,
            BigDecimal interestAmount,
            BigDecimal amountPaid,
            String status
    ) {
        InterestLedger ledger = instantiate(InterestLedger.class);
        set(ledger, "publicId", UUID.fromString("33333333-3333-4333-8333-333333333333"));
        set(ledger, "creditLimitPublicId", creditLimitPublicId);
        set(ledger, "basePrincipal", new BigDecimal("1000000"));
        set(ledger, "dueDate", dueDate);
        set(ledger, "interestAmount", interestAmount);
        set(ledger, "amountPaid", amountPaid);
        set(ledger, "status", status);
        return ledger;
    }

    public static PrincipalRepaymentLedger principalLedger(UUID creditLimitPublicId, LocalDate dueDate, String status) {
        PrincipalRepaymentLedger ledger = instantiate(PrincipalRepaymentLedger.class);
        set(ledger, "publicId", UUID.fromString("44444444-4444-4444-8444-444444444444"));
        set(ledger, "creditLimitPublicId", creditLimitPublicId);
        set(ledger, "orderPublicId", UUID.fromString("55555555-5555-4555-8555-555555555555"));
        set(ledger, "dueDate", dueDate);
        set(ledger, "principalAmount", new BigDecimal("700000"));
        set(ledger, "amountPaid", BigDecimal.ZERO);
        set(ledger, "status", status);
        return ledger;
    }

    public static WalletTransaction walletTransaction(UUID walletPublicId, String type, BigDecimal amount, LocalDateTime transactedAt) {
        WalletTransaction transaction = instantiate(WalletTransaction.class);
        set(transaction, "publicId", UUID.fromString("66666666-6666-4666-8666-666666666666"));
        set(transaction, "walletPublicId", walletPublicId);
        set(transaction, "transactionType", type);
        set(transaction, "amount", amount);
        set(transaction, "balanceAfter", new BigDecimal("900000"));
        set(transaction, "transactedAt", transactedAt);
        set(transaction, "createdAt", transactedAt);
        return transaction;
    }

    public static CreditLimitApplication application(UUID userPublicId, ApplicationStatus status) {
        CreditLimitApplication application = CreditLimitApplication.create(userPublicId, new BigDecimal("1000000"));
        set(application, "status", status);
        return application;
    }

    public static void set(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("테스트 엔티티 생성 실패: " + type.getName(), exception);
        }
    }
}
