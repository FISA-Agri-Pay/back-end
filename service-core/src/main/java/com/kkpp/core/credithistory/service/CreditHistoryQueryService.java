package com.kkpp.core.credithistory.service;

import com.kkpp.core.credithistory.domain.CreditUsageLedger;
import com.kkpp.core.credithistory.dto.CreditRepaymentHistoryResponse;
import com.kkpp.core.credithistory.dto.CreditUsageHistoryResponse;
import com.kkpp.core.credithistory.repository.CreditUsageHistoryRepository;
import com.kkpp.core.credithistory.repository.CreditUsageHistoryRepository.CreditUsageHistoryRow;
import com.kkpp.core.user.domain.User;
import com.kkpp.core.user.repository.UserRepository;
import com.kkpp.core.wallet.domain.WalletTransaction;
import com.kkpp.core.wallet.exception.WalletErrorCode;
import com.kkpp.core.wallet.exception.WalletException;
import com.kkpp.core.wallet.repository.WalletRepository;
import com.kkpp.core.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditHistoryQueryService {

    private static final int HISTORY_LIMIT = 20;
    private static final List<String> REPAYMENT_TRANSACTION_TYPES = List.of(
            WalletTransaction.TYPE_INTEREST_PAYMENT,
            WalletTransaction.TYPE_PRINCIPAL_PAYMENT
    );

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CreditUsageHistoryRepository creditUsageHistoryRepository;

    public List<CreditUsageHistoryResponse> getMyUsageHistories(Long authenticatedUserId) {
        // 인증 컨텍스트의 Long userId를 DB 조회 기준인 userPublicId로 변환한 뒤 내역을 제한합니다.
        UUID userPublicId = resolveUserPublicId(authenticatedUserId);
        return creditUsageHistoryRepository.findLatestUsageHistories(userPublicId, HISTORY_LIMIT)
                .stream()
                .map(this::toUsageHistoryResponse)
                .toList();
    }

    public List<CreditRepaymentHistoryResponse> getMyRepaymentHistories(Long authenticatedUserId) {
        UUID userPublicId = resolveUserPublicId(authenticatedUserId);
        return walletRepository.findByUserPublicId(userPublicId)
                // 지갑이 아직 없으면 화면 탭이 깨지지 않도록 예외 대신 빈 배열을 반환합니다.
                .map(wallet -> walletTransactionRepository
                        .findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(
                                wallet.getPublicId(),
                                REPAYMENT_TRANSACTION_TYPES
                        )
                        .stream()
                        .map(this::toRepaymentHistoryResponse)
                        .toList())
                .orElseGet(List::of);
    }

    private UUID resolveUserPublicId(Long authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new WalletException(WalletErrorCode.USER_NOT_FOUND));
        return user.getPublicId();
    }

    private CreditUsageHistoryResponse toUsageHistoryResponse(CreditUsageHistoryRow row) {
        return new CreditUsageHistoryResponse(
                row.getHistoryPublicId(),
                row.getUsedAt(),
                usageTitle(row.getFirstProductName(), row.getItemCount()),
                usageDisplayAmount(row.getAmount(), row.getUsageType()),
                row.getUsageType(),
                row.getOrderStatus(),
                row.getDeliveryStatus(),
                displayStatus(row.getOrderStatus(), row.getDeliveryStatus())
        );
    }

    private String usageTitle(String firstProductName, long itemCount) {
        // 주문 상품이 여러 개면 첫 상품을 대표명으로 쓰고 나머지는 "외 N개"로 축약합니다.
        if (!StringUtils.hasText(firstProductName)) {
            return "외상 이용";
        }
        if (itemCount <= 1) {
            return firstProductName;
        }
        return firstProductName + " 외 " + (itemCount - 1) + "개";
    }

    private BigDecimal usageDisplayAmount(BigDecimal amount, String usageType) {
        if (amount == null) {
            return null;
        }
        // DB 원장 금액은 양수 저장이지만, 화면에서는 외상 사용을 차감처럼 보여주기 위해 음수로 응답합니다.
        if (CreditUsageLedger.TYPE_PURCHASE.equals(usageType)) {
            return amount.abs().negate();
        }
        if (CreditUsageLedger.TYPE_CANCEL.equals(usageType)) {
            return amount.abs();
        }
        return amount;
    }

    private String displayStatus(String orderStatus, String deliveryStatus) {
        // 배송 상태가 있으면 배송 상태를 우선 표시하고, 없을 때만 주문 상태로 보완합니다.
        if ("CANCELLED".equals(orderStatus) || "CANCELLED".equals(deliveryStatus)) {
            return "취소";
        }
        if (StringUtils.hasText(deliveryStatus)) {
            return switch (deliveryStatus) {
                case "PREPARING" -> "주문확인";
                case "SHIPPING" -> "배송중";
                case "DELIVERED" -> "배송완료";
                default -> deliveryStatus;
            };
        }
        if ("CONFIRMED".equals(orderStatus)) {
            return "주문확인";
        }
        return orderStatus;
    }

    private CreditRepaymentHistoryResponse toRepaymentHistoryResponse(WalletTransaction transaction) {
        return new CreditRepaymentHistoryResponse(
                transaction.getPublicId(),
                transaction.getTransactedAt(),
                repaymentTitle(transaction),
                transaction.getTransactionType(),
                repaymentDisplayAmount(transaction.getAmount())
        );
    }

    private String repaymentTitle(WalletTransaction transaction) {
        return switch (transaction.getTransactionType()) {
            case WalletTransaction.TYPE_INTEREST_PAYMENT -> interestPaymentTitle(transaction.getTransactedAt());
            case WalletTransaction.TYPE_PRINCIPAL_PAYMENT -> "원금 상환";
            default -> transaction.getDescription() == null
                    ? transaction.getTransactionType()
                    : transaction.getDescription();
        };
    }

    private String interestPaymentTitle(LocalDateTime transactedAt) {
        if (transactedAt == null) {
            return "이자 상환";
        }
        return transactedAt.minusMonths(1).getMonthValue() + "월 이자 상환";
    }

    private BigDecimal repaymentDisplayAmount(BigDecimal amount) {
        // 상환/납부 탭의 거래는 모두 지갑에서 빠져나간 금액으로 표시합니다.
        return amount == null ? null : amount.abs().negate();
    }
}
