package com.kkpp.core.wallet.service;

import com.kkpp.core.user.domain.User;
import com.kkpp.core.user.repository.UserRepository;
import com.kkpp.core.wallet.domain.CreditLimit;
import com.kkpp.core.wallet.domain.InterestLedger;
import com.kkpp.core.wallet.domain.PrincipalRepaymentLedger;
import com.kkpp.core.wallet.domain.Wallet;
import com.kkpp.core.wallet.domain.WalletTransaction;
import com.kkpp.core.wallet.dto.WalletCreditSummaryResponse;
import com.kkpp.core.wallet.dto.WalletMeResponse;
import com.kkpp.core.wallet.exception.WalletErrorCode;
import com.kkpp.core.wallet.exception.WalletException;
import com.kkpp.core.wallet.repository.CreditLimitRepository;
import com.kkpp.core.wallet.repository.InterestLedgerRepository;
import com.kkpp.core.wallet.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.core.wallet.repository.WalletRepository;
import com.kkpp.core.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletQueryService {

    private static final int USAGE_RATE_SCALE = 1;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO_USAGE_RATE = BigDecimal.ZERO.setScale(USAGE_RATE_SCALE);
    private static final BigDecimal MAX_USAGE_RATE = BigDecimal.valueOf(100).setScale(USAGE_RATE_SCALE);
    private static final List<String> UNPAID_LEDGER_STATUSES = List.of(
            InterestLedger.STATUS_UPCOMING,
            InterestLedger.STATUS_PARTIAL,
            InterestLedger.STATUS_OVERDUE
    );
    private static final List<String> WALLET_HISTORY_TYPES = List.of(
            WalletTransaction.TYPE_INTEREST_PAYMENT,
            WalletTransaction.TYPE_PRINCIPAL_PAYMENT
    );

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CreditLimitRepository creditLimitRepository;
    private final InterestLedgerRepository interestLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletCreditSummaryResponse getMyCreditSummary(Long authenticatedUserId) {
        // 홈 한도 카드는 지갑 생성 여부와 무관하게 최신 활성 한도 1건만 기준으로 구성합니다.
        UUID userPublicId = resolveUserPublicId(authenticatedUserId);
        return creditLimitRepository
                .findFirstByUserPublicIdAndStatusOrderByCreatedAtDescIdDesc(userPublicId, CreditLimit.STATUS_ACTIVE)
                .map(this::toCreditSummaryResponse)
                .orElseGet(this::emptyCreditSummaryResponse);
    }

    public WalletMeResponse getMyWallet(Long authenticatedUserId) {
        // 인증 토큰은 내부 Long userId만 제공하므로, 지갑/원장 조회 기준인 userPublicId로 변환합니다.
        UUID userPublicId = resolveUserPublicId(authenticatedUserId);
        Wallet wallet = walletRepository.findByUserPublicId(userPublicId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<CreditLimit> activeCreditLimit = creditLimitRepository
                .findFirstByUserPublicIdAndStatusOrderByCreatedAtDescIdDesc(userPublicId, CreditLimit.STATUS_ACTIVE);
        Optional<InterestLedger> unpaidInterestLedger = activeCreditLimit
                .flatMap(this::findNearestUnpaidInterestLedger);
        Optional<PrincipalRepaymentLedger> unpaidPrincipalLedger = activeCreditLimit
                .flatMap(this::findNearestUnpaidPrincipalLedger);

        List<WalletMeResponse.Transaction> transactions = walletTransactionRepository
                .findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(
                        wallet.getPublicId(),
                        WALLET_HISTORY_TYPES
                )
                .stream()
                .map(this::toTransactionResponse)
                .toList();

        return new WalletMeResponse(
                wallet.getPublicId(),
                wallet.getDepositBankName(),
                wallet.getDepositAccountNumber(),
                wallet.getBalance(),
                nextRepaymentDate(unpaidInterestLedger, unpaidPrincipalLedger),
                unpaidInterestLedger.map(this::toMonthlyInterestResponse).orElse(null),
                activeCreditLimit.map(creditLimit -> toPrincipalResponse(creditLimit, unpaidPrincipalLedger))
                        .orElse(null),
                transactions
        );
    }

    private UUID resolveUserPublicId(Long authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.USER_NOT_FOUND));
        return user.getPublicId();
    }

    private Optional<InterestLedger> findNearestUnpaidInterestLedger(CreditLimit creditLimit) {
        // 미래 예정 원장을 우선 고르고, 미래 건이 없으면 가장 최근 연체 원장을 화면에 노출합니다.
        return interestLedgerRepository.findNearestUnpaidLedger(
                creditLimit.getPublicId(),
                UNPAID_LEDGER_STATUSES
        );
    }

    private Optional<PrincipalRepaymentLedger> findNearestUnpaidPrincipalLedger(CreditLimit creditLimit) {
        // 이자 원장과 같은 기준으로 다음 상환 예정일 후보를 선택합니다.
        return principalRepaymentLedgerRepository.findNearestUnpaidLedger(
                creditLimit.getPublicId(),
                UNPAID_LEDGER_STATUSES
        );
    }

    private LocalDate nextRepaymentDate(
            Optional<InterestLedger> interestLedger,
            Optional<PrincipalRepaymentLedger> principalLedger
    ) {
        return List.of(
                        interestLedger.map(InterestLedger::getDueDate),
                        principalLedger.map(PrincipalRepaymentLedger::getDueDate)
                )
                .stream()
                .flatMap(Optional::stream)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private WalletMeResponse.MonthlyInterest toMonthlyInterestResponse(InterestLedger ledger) {
        return new WalletMeResponse.MonthlyInterest(
                ledger.getDueDate(),
                ledger.getUnpaidAmount(),
                ledger.getStatus()
        );
    }

    private WalletCreditSummaryResponse toCreditSummaryResponse(CreditLimit creditLimit) {
        BigDecimal totalLimit = zeroIfNull(creditLimit.getTotalLimit());
        BigDecimal usedAmount = zeroIfNull(creditLimit.getUsedAmount());
        BigDecimal remainingAmount = totalLimit.subtract(usedAmount);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            // DB 제약이 깨진 데이터가 들어와도 홈 progress 영역이 음수 잔여 한도를 표시하지 않도록 방어합니다.
            remainingAmount = BigDecimal.ZERO;
        }

        return new WalletCreditSummaryResponse(
                true,
                creditLimit.getPublicId(),
                totalLimit,
                usedAmount,
                remainingAmount,
                usageRate(usedAmount, totalLimit),
                creditLimit.getStatus()
        );
    }

    private WalletCreditSummaryResponse emptyCreditSummaryResponse() {
        return new WalletCreditSummaryResponse(
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ZERO_USAGE_RATE,
                null
        );
    }

    private BigDecimal usageRate(BigDecimal usedAmount, BigDecimal totalLimit) {
        if (totalLimit == null || totalLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_USAGE_RATE;
        }
        BigDecimal rate = zeroIfNull(usedAmount)
                .multiply(ONE_HUNDRED)
                .divide(totalLimit, USAGE_RATE_SCALE, RoundingMode.HALF_UP);
        // 프론트 progress bar 값으로 바로 쓰이므로 비정상 데이터에서도 100%를 넘기지 않습니다.
        return rate.compareTo(MAX_USAGE_RATE) > 0 ? MAX_USAGE_RATE : rate;
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private WalletMeResponse.Principal toPrincipalResponse(
            CreditLimit creditLimit,
            Optional<PrincipalRepaymentLedger> principalLedger
    ) {
        // MVP 기준 원금 잔액은 상환 원장 합산이 아니라 credit_limits.used_amount를 화면에 노출합니다.
        return new WalletMeResponse.Principal(
                principalLedger.map(PrincipalRepaymentLedger::getDueDate)
                        .orElse(creditLimit.getPrincipalDueDate()),
                creditLimit.getUsedAmount(),
                principalLedger.map(PrincipalRepaymentLedger::getStatus)
                        .orElse(fallbackPrincipalStatus(creditLimit))
        );
    }

    private String fallbackPrincipalStatus(CreditLimit creditLimit) {
        if (creditLimit.getUsedAmount() == null || creditLimit.getUsedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return PrincipalRepaymentLedger.STATUS_UPCOMING;
    }

    private WalletMeResponse.Transaction toTransactionResponse(WalletTransaction transaction) {
        return new WalletMeResponse.Transaction(
                transaction.getPublicId(),
                transaction.getTransactionType(),
                title(transaction),
                displayAmount(transaction),
                transaction.getTransactedAt()
        );
    }

    private String title(WalletTransaction transaction) {
        return switch (transaction.getTransactionType()) {
            case WalletTransaction.TYPE_INTEREST_PAYMENT -> interestPaymentTitle(transaction.getTransactedAt());
            case WalletTransaction.TYPE_PRINCIPAL_PAYMENT -> "원금 상환";
            case WalletTransaction.TYPE_DEPOSIT -> "지갑 입금";
            default -> transaction.getDescription() == null ? transaction.getTransactionType() : transaction.getDescription();
        };
    }

    private String interestPaymentTitle(LocalDateTime transactedAt) {
        if (transactedAt == null) {
            return "이자 상환";
        }
        return transactedAt.minusMonths(1).getMonthValue() + "월 이자 상환";
    }

    private BigDecimal displayAmount(WalletTransaction transaction) {
        BigDecimal amount = transaction.getAmount();
        if (WalletTransaction.TYPE_INTEREST_PAYMENT.equals(transaction.getTransactionType())
                || WalletTransaction.TYPE_PRINCIPAL_PAYMENT.equals(transaction.getTransactionType())) {
            // DB 값의 부호와 무관하게 프론트 내역에서는 상환성 거래를 항상 음수로 표시합니다.
            return amount == null ? null : amount.abs().negate();
        }
        return amount;
    }
}
