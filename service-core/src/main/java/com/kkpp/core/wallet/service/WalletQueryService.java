package com.kkpp.core.wallet.service;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import com.kkpp.core.global.logging.LogMaskingUtils;
import com.kkpp.core.global.logging.LoggingTimeUtils;
import com.kkpp.core.global.tracing.TracingSupport;
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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
    private final CreditLimitApplicationRepository creditLimitApplicationRepository;
    private final InterestLedgerRepository interestLedgerRepository;
    private final PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TracingSupport tracingSupport;

    public WalletCreditSummaryResponse getMyCreditSummary(Long authenticatedUserId) {
        long startedAtNanos = System.nanoTime();
        Span span = tracingSupport.startSpan("service-core.wallet.credit-summary");
        try (Scope ignored = span.makeCurrent()) {
            return getMyCreditSummaryWithSpan(authenticatedUserId, startedAtNanos, span);
        } catch (RuntimeException exception) {
            tracingSupport.recordException(span, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private WalletCreditSummaryResponse getMyCreditSummaryWithSpan(
            Long authenticatedUserId,
            long startedAtNanos,
            Span span
    ) {
        // 홈 화면의 한도 요약 조회는 모니터링 대상 API라 별도 span과 구조화 로그를 남깁니다.
        span.setAttribute("kkpp.event", "wallet.credit.summary");
        span.setAttribute("kkpp.user.id", authenticatedUserId);
        log.atInfo()
                .addKeyValue("event", "wallet.credit.summary.started")
                .addKeyValue("userId", authenticatedUserId)
                .log("한도 요약 조회를 시작했습니다.");

        try {
            User user = userRepository.findById(authenticatedUserId)
                    .orElseThrow(() -> new WalletException(WalletErrorCode.USER_NOT_FOUND));
            UUID userPublicId = user.getPublicId();
            span.setAttribute("kkpp.user.public_id.masked", LogMaskingUtils.maskIdentifier(userPublicId));
            String userName = user.getName();

            Optional<CreditLimit> activeCreditLimit = findLatestUsableActiveCreditLimit(userPublicId);
            if (activeCreditLimit.isPresent()) {
                WalletCreditSummaryResponse response = toCreditSummaryResponse(activeCreditLimit.get(), userName);
                logCreditSummaryCompleted(authenticatedUserId, userPublicId, response, startedAtNanos, span);
                return response;
            }

            ApplicationStatus applicationStatus = creditLimitApplicationRepository
                    .findTopByUserPublicIdOrderByAppliedAtDesc(userPublicId)
                    .map(app -> app.getStatus())
                    .orElse(null);
            WalletCreditSummaryResponse response = emptyCreditSummaryResponse(applicationStatus, userName);
            logCreditSummaryCompleted(authenticatedUserId, userPublicId, response, startedAtNanos, span);
            return response;
        } catch (RuntimeException exception) {
            // 실패 시에는 금액이나 개인정보 대신 실패 구간과 처리 시간을 남깁니다.
            span.setAttribute("kkpp.failure_state", "QUERYING_CREDIT_SUMMARY");
            span.setAttribute("kkpp.duration_ms", LoggingTimeUtils.elapsedMillis(startedAtNanos));
            log.atError()
                    .addKeyValue("event", "wallet.credit.summary.failed")
                    .addKeyValue("userId", authenticatedUserId)
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .addKeyValue("failureState", "QUERYING_CREDIT_SUMMARY")
                    .setCause(exception)
                    .log("한도 요약 조회 중 오류가 발생했습니다.");
            throw exception;
        }
    }

    public WalletMeResponse getMyWallet(Long authenticatedUserId) {
        UUID userPublicId = resolveUserPublicId(authenticatedUserId);
        Wallet wallet = walletRepository.findByUserPublicId(userPublicId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.WALLET_NOT_FOUND));

        Optional<CreditLimit> activeCreditLimit = findLatestUsableActiveCreditLimit(userPublicId);
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

    private Optional<CreditLimit> findLatestUsableActiveCreditLimit(UUID userPublicId) {
        return creditLimitRepository.findLatestUsableActiveLimit(
                userPublicId,
                CreditLimit.STATUS_ACTIVE,
                LocalDate.now()
        );
    }

    private Optional<InterestLedger> findNearestUnpaidInterestLedger(CreditLimit creditLimit) {
        return interestLedgerRepository.findNearestUnpaidLedger(
                creditLimit.getPublicId(),
                UNPAID_LEDGER_STATUSES
        );
    }

    private Optional<PrincipalRepaymentLedger> findNearestUnpaidPrincipalLedger(CreditLimit creditLimit) {
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

    private WalletCreditSummaryResponse toCreditSummaryResponse(CreditLimit creditLimit, String userName) {
        BigDecimal totalLimit = zeroIfNull(creditLimit.getTotalLimit());
        BigDecimal usedAmount = zeroIfNull(creditLimit.getUsedAmount());
        BigDecimal remainingAmount = totalLimit.subtract(usedAmount);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
            remainingAmount = BigDecimal.ZERO;
        }

        return new WalletCreditSummaryResponse(
                userName,
                true,
                creditLimit.getPublicId(),
                totalLimit,
                usedAmount,
                remainingAmount,
                usageRate(usedAmount, totalLimit),
                creditLimit.getStatus(),
                ApplicationStatus.APPROVED.name()
        );
    }

    private WalletCreditSummaryResponse emptyCreditSummaryResponse(ApplicationStatus applicationStatus, String userName) {
        return new WalletCreditSummaryResponse(
                userName,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ZERO_USAGE_RATE,
                null,
                applicationStatus == null ? null : applicationStatus.name()
        );
    }

    private void logCreditSummaryCompleted(
            Long authenticatedUserId,
            UUID userPublicId,
            WalletCreditSummaryResponse response,
            long startedAtNanos,
            Span span
    ) {
        // 금액은 응답으로 충분히 확인 가능하므로 로그와 span에는 상태 중심의 안전한 컨텍스트만 남깁니다.
        span.setAttribute("kkpp.has_active_limit", response.hasActiveLimit());
        span.setAttribute("kkpp.credit_limit.public_id.masked", LogMaskingUtils.maskIdentifier(response.creditLimitPublicId()));
        if (response.applicationStatus() != null) {
            span.setAttribute("kkpp.application.status", response.applicationStatus());
        }
        span.setAttribute("kkpp.duration_ms", LoggingTimeUtils.elapsedMillis(startedAtNanos));
        log.atInfo()
                .addKeyValue("event", "wallet.credit.summary.completed")
                .addKeyValue("userId", authenticatedUserId)
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("hasActiveLimit", response.hasActiveLimit())
                .addKeyValue("creditLimitPublicId", LogMaskingUtils.maskIdentifier(response.creditLimitPublicId()))
                .addKeyValue("applicationStatus", response.applicationStatus())
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .log("한도 요약 조회를 완료했습니다.");
    }

    private BigDecimal usageRate(BigDecimal usedAmount, BigDecimal totalLimit) {
        if (totalLimit == null || totalLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO_USAGE_RATE;
        }
        BigDecimal normalizedUsedAmount = zeroIfNull(usedAmount).max(BigDecimal.ZERO);
        BigDecimal rate = normalizedUsedAmount
                .multiply(ONE_HUNDRED)
                .divide(totalLimit, USAGE_RATE_SCALE, RoundingMode.HALF_UP);
        if (rate.compareTo(ZERO_USAGE_RATE) < 0) {
            return ZERO_USAGE_RATE;
        }
        return rate.compareTo(MAX_USAGE_RATE) > 0 ? MAX_USAGE_RATE : rate;
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private WalletMeResponse.Principal toPrincipalResponse(
            CreditLimit creditLimit,
            Optional<PrincipalRepaymentLedger> principalLedger
    ) {
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
            return amount == null ? null : amount.abs().negate();
        }
        return amount;
    }
}
