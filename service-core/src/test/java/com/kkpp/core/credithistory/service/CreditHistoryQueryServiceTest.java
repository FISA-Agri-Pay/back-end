package com.kkpp.core.credithistory.service;

import static com.kkpp.core.testsupport.TestEntityFactory.user;
import static com.kkpp.core.testsupport.TestEntityFactory.set;
import static com.kkpp.core.testsupport.TestEntityFactory.wallet;
import static com.kkpp.core.testsupport.TestEntityFactory.walletTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kkpp.core.credithistory.domain.CreditUsageLedger;
import com.kkpp.core.credithistory.dto.CreditRepaymentHistoryResponse;
import com.kkpp.core.credithistory.dto.CreditUsageHistoryResponse;
import com.kkpp.core.credithistory.repository.CreditUsageHistoryRepository;
import com.kkpp.core.credithistory.repository.CreditUsageHistoryRepository.CreditUsageHistoryRow;
import com.kkpp.core.user.repository.UserRepository;
import com.kkpp.core.wallet.domain.WalletTransaction;
import com.kkpp.core.wallet.exception.WalletErrorCode;
import com.kkpp.core.wallet.exception.WalletException;
import com.kkpp.core.wallet.repository.WalletRepository;
import com.kkpp.core.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditHistoryQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID WALLET_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private CreditUsageHistoryRepository creditUsageHistoryRepository;

    private CreditHistoryQueryService creditHistoryQueryService;

    @BeforeEach
    void setUp() {
        creditHistoryQueryService = new CreditHistoryQueryService(
                userRepository,
                walletRepository,
                walletTransactionRepository,
                creditUsageHistoryRepository
        );
    }

    @Test
    void getMyUsageHistoriesFormatsPurchaseAmountTitleAndDeliveryStatus() {
        CreditUsageHistoryRow row = usageRow(
                "모종 세트",
                3,
                CreditUsageLedger.TYPE_PURCHASE,
                "CONFIRMED",
                "SHIPPING",
                new BigDecimal("120000")
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditUsageHistoryRepository.findLatestUsageHistories(USER_PUBLIC_ID, 20)).thenReturn(List.of(row));

        List<CreditUsageHistoryResponse> responses = creditHistoryQueryService.getMyUsageHistories(USER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("모종 세트 외 2개");
        assertThat(responses.getFirst().amount()).isEqualByComparingTo("-120000");
        assertThat(responses.getFirst().displayStatus()).isEqualTo("배송중");
    }

    @Test
    void getMyUsageHistoriesFormatsCancelAmountAndCancelledStatus() {
        CreditUsageHistoryRow row = usageRow(
                null,
                0,
                CreditUsageLedger.TYPE_CANCEL,
                "CANCELLED",
                null,
                new BigDecimal("50000")
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditUsageHistoryRepository.findLatestUsageHistories(USER_PUBLIC_ID, 20)).thenReturn(List.of(row));

        List<CreditUsageHistoryResponse> responses = creditHistoryQueryService.getMyUsageHistories(USER_ID);

        assertThat(responses.getFirst().title()).isEqualTo("외상 이용");
        assertThat(responses.getFirst().amount()).isEqualByComparingTo("50000");
        assertThat(responses.getFirst().displayStatus()).isEqualTo("취소");
    }

    @Test
    void getMyUsageHistoriesFormatsSingleProductNullAmountAndConfirmedFallback() {
        CreditUsageHistoryRow row = usageRow(
                "비료",
                1,
                "ADJUSTMENT",
                "CONFIRMED",
                null,
                null
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditUsageHistoryRepository.findLatestUsageHistories(USER_PUBLIC_ID, 20)).thenReturn(List.of(row));

        List<CreditUsageHistoryResponse> responses = creditHistoryQueryService.getMyUsageHistories(USER_ID);

        assertThat(responses.getFirst().title()).isEqualTo("비료");
        assertThat(responses.getFirst().amount()).isNull();
        assertThat(responses.getFirst().displayStatus()).isEqualTo("주문확인");
    }

    @Test
    void getMyUsageHistoriesKeepsAdjustmentAmountAndDeliveryCancelledStatus() {
        CreditUsageHistoryRow row = usageRow(
                "비료",
                1,
                "ADJUSTMENT",
                "CONFIRMED",
                "CANCELLED",
                new BigDecimal("7000")
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditUsageHistoryRepository.findLatestUsageHistories(USER_PUBLIC_ID, 20)).thenReturn(List.of(row));

        List<CreditUsageHistoryResponse> responses = creditHistoryQueryService.getMyUsageHistories(USER_ID);

        assertThat(responses.getFirst().amount()).isEqualByComparingTo("7000");
        assertThat(responses.getFirst().displayStatus()).isEqualTo("취소");
    }


    @Test
    void getMyUsageHistoriesReturnsDeliveryStatusLabelsAndUnknownFallback() {
        CreditUsageHistoryRow preparing = usageRow("비료", 1, CreditUsageLedger.TYPE_PURCHASE, "CONFIRMED", "PREPARING", new BigDecimal("1000"));
        CreditUsageHistoryRow delivered = usageRow("농약", 1, CreditUsageLedger.TYPE_PURCHASE, "CONFIRMED", "DELIVERED", new BigDecimal("2000"));
        CreditUsageHistoryRow custom = usageRow("상토", 1, CreditUsageLedger.TYPE_PURCHASE, "CONFIRMED", "RETURNING", new BigDecimal("3000"));
        CreditUsageHistoryRow orderFallback = usageRow("모종", 1, CreditUsageLedger.TYPE_PURCHASE, "REVIEWING", null, new BigDecimal("4000"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditUsageHistoryRepository.findLatestUsageHistories(USER_PUBLIC_ID, 20))
                .thenReturn(List.of(preparing, delivered, custom, orderFallback));

        List<CreditUsageHistoryResponse> responses = creditHistoryQueryService.getMyUsageHistories(USER_ID);

        assertThat(responses).extracting(CreditUsageHistoryResponse::displayStatus)
                .containsExactly("주문확인", "배송완료", "RETURNING", "REVIEWING");
    }

    @Test
    void getMyRepaymentHistoriesReturnsEmptyListWhenWalletDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());

        List<CreditRepaymentHistoryResponse> responses = creditHistoryQueryService.getMyRepaymentHistories(USER_ID);

        assertThat(responses).isEmpty();
    }

    @Test
    void getMyRepaymentHistoriesFormatsInterestAndPrincipalTransactions() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(wallet(WALLET_PUBLIC_ID, USER_PUBLIC_ID, new BigDecimal("300000"))));
        when(walletTransactionRepository.findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(
                eq(WALLET_PUBLIC_ID),
                anyCollection()
        )).thenReturn(List.of(
                walletTransaction(
                        WALLET_PUBLIC_ID,
                        WalletTransaction.TYPE_INTEREST_PAYMENT,
                        new BigDecimal("10000"),
                        LocalDateTime.of(2026, 5, 11, 10, 0)
                ),
                walletTransaction(
                        WALLET_PUBLIC_ID,
                        WalletTransaction.TYPE_PRINCIPAL_PAYMENT,
                        new BigDecimal("-70000"),
                        LocalDateTime.of(2026, 5, 12, 10, 0)
                )
        ));

        List<CreditRepaymentHistoryResponse> responses = creditHistoryQueryService.getMyRepaymentHistories(USER_ID);

        assertThat(responses).extracting(CreditRepaymentHistoryResponse::title)
                .containsExactly("4월 이자 상환", "원금 상환");
        assertThat(responses).extracting(CreditRepaymentHistoryResponse::amount)
                .containsExactly(new BigDecimal("-10000"), new BigDecimal("-70000"));
    }

    @Test
    void getMyRepaymentHistoriesFormatsNullDateNullAmountAndDefaultTitle() {
        WalletTransaction interestWithoutDate = walletTransaction(
                WALLET_PUBLIC_ID,
                WalletTransaction.TYPE_INTEREST_PAYMENT,
                null,
                null
        );
        WalletTransaction custom = walletTransaction(
                WALLET_PUBLIC_ID,
                "MANUAL",
                new BigDecimal("3000"),
                LocalDateTime.of(2026, 5, 12, 10, 0)
        );
        WalletTransaction customWithoutDescription = walletTransaction(
                WALLET_PUBLIC_ID,
                "ETC",
                new BigDecimal("4000"),
                LocalDateTime.of(2026, 5, 13, 10, 0)
        );
        set(custom, "description", "수기 조정");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(walletRepository.findByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(wallet(WALLET_PUBLIC_ID, USER_PUBLIC_ID, new BigDecimal("300000"))));
        when(walletTransactionRepository.findTop20ByWalletPublicIdAndTransactionTypeInOrderByTransactedAtDesc(
                eq(WALLET_PUBLIC_ID),
                anyCollection()
        )).thenReturn(List.of(interestWithoutDate, custom, customWithoutDescription));

        List<CreditRepaymentHistoryResponse> responses = creditHistoryQueryService.getMyRepaymentHistories(USER_ID);

        assertThat(responses).extracting(CreditRepaymentHistoryResponse::title)
                .containsExactly("이자 상환", "수기 조정", "ETC");
        assertThat(responses.getFirst().amount()).isNull();
        assertThat(responses.get(1).amount()).isEqualByComparingTo("-3000");
        assertThat(responses.get(2).amount()).isEqualByComparingTo("-4000");
    }

    @Test
    void getMyUsageHistoriesThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditHistoryQueryService.getMyUsageHistories(USER_ID))
                .isInstanceOf(WalletException.class)
                .extracting("errorCode")
                .isEqualTo(WalletErrorCode.USER_NOT_FOUND);
    }

    private CreditUsageHistoryRow usageRow(
            String firstProductName,
            long itemCount,
            String usageType,
            String orderStatus,
            String deliveryStatus,
            BigDecimal amount
    ) {
        CreditUsageHistoryRow row = mock(CreditUsageHistoryRow.class);
        when(row.getHistoryPublicId()).thenReturn(UUID.fromString("33333333-3333-4333-8333-333333333333"));
        when(row.getUsedAt()).thenReturn(LocalDateTime.of(2026, 5, 11, 10, 0));
        when(row.getFirstProductName()).thenReturn(firstProductName);
        when(row.getItemCount()).thenReturn(itemCount);
        when(row.getUsageType()).thenReturn(usageType);
        when(row.getOrderStatus()).thenReturn(orderStatus);
        when(row.getDeliveryStatus()).thenReturn(deliveryStatus);
        when(row.getAmount()).thenReturn(amount);
        return row;
    }
}
