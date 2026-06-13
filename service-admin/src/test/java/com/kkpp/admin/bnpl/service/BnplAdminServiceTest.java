package com.kkpp.admin.bnpl.service;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.bnplAdminUser;
import static com.kkpp.admin.testsupport.AdminTestEntityFactory.bnplCreditLimit;
import static com.kkpp.admin.testsupport.AdminTestEntityFactory.bnplUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.kkpp.admin.bnpl.domain.BnplNotification;
import com.kkpp.admin.bnpl.domain.NotificationChannel;
import com.kkpp.admin.bnpl.domain.OverdueStage;
import com.kkpp.admin.bnpl.dto.OverdueAlertRequest;
import com.kkpp.admin.bnpl.dto.RepaymentAlertRequest;
import com.kkpp.admin.bnpl.repository.BnplAdminUserRepository;
import com.kkpp.admin.bnpl.repository.BnplAuditLogRepository;
import com.kkpp.admin.bnpl.repository.BnplCreditLimitRepository;
import com.kkpp.admin.bnpl.repository.BnplNotificationRepository;
import com.kkpp.admin.bnpl.repository.BnplOrderRepository;
import com.kkpp.admin.bnpl.repository.BnplUserRepository;
import com.kkpp.admin.bnpl.repository.InterestLedgerRepository;
import com.kkpp.admin.bnpl.repository.LoanOverdueLedgerRepository;
import com.kkpp.admin.bnpl.repository.PrincipalRepaymentLedgerRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.security.auth.AuthUserInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class BnplAdminServiceTest {

    private static final UUID ADMIN_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID USER_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Mock
    private BnplCreditLimitRepository creditLimitRepository;

    @Mock
    private InterestLedgerRepository interestLedgerRepository;

    @Mock
    private PrincipalRepaymentLedgerRepository principalRepaymentLedgerRepository;

    @Mock
    private LoanOverdueLedgerRepository overdueRepository;

    @Mock
    private BnplNotificationRepository notificationRepository;

    @Mock
    private BnplAuditLogRepository auditLogRepository;

    @Mock
    private BnplAdminUserRepository adminUserRepository;

    @Mock
    private BnplUserRepository userRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private BnplOrderRepository orderRepository;

    @Mock
    private TransactionStatus transactionStatus;

    private BnplAdminService bnplAdminService;

    @BeforeEach
    void setUp() {
        bnplAdminService = new BnplAdminService(
                creditLimitRepository,
                interestLedgerRepository,
                principalRepaymentLedgerRepository,
                overdueRepository,
                notificationRepository,
                auditLogRepository,
                adminUserRepository,
                userRepository,
                transactionManager,
                orderRepository
        );
    }

    @Test
    void getBnplSummaryAggregatesUsageRepaymentAndOverdueStatus() {
        when(orderRepository.countBnplUsageOrders()).thenReturn(5L);
        when(creditLimitRepository.sumActiveUsedAmount()).thenReturn(new BigDecimal("1000000"));
        when(interestLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("10000"));
        when(principalRepaymentLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new BigDecimal("90000"));
        when(overdueRepository.sumUnresolvedOverdueAmount()).thenReturn(new BigDecimal("30000001"));
        when(creditLimitRepository.countNormalUsers()).thenReturn(7L);
        when(overdueRepository.countDistinctOverdueUsers()).thenReturn(2L);
        when(creditLimitRepository.countSuspendedUsers()).thenReturn(1L);

        var response = bnplAdminService.getBnplSummary();

        assertThat(response.totalUsageCount()).isEqualTo(5);
        assertThat(response.scheduledRepayment()).isEqualByComparingTo("100000");
        assertThat(response.isOverdueAlert()).isTrue();
        assertThat(response.statusCounts().NORMAL()).isEqualTo(7);
    }

    @Test
    void getBnplSummaryDoesNotRaiseAlertAtThreshold() {
        when(orderRepository.countBnplUsageOrders()).thenReturn(0L);
        when(creditLimitRepository.sumActiveUsedAmount()).thenReturn(BigDecimal.ZERO);
        when(interestLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(principalRepaymentLedgerRepository.sumScheduledRepaymentThisMonth(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(overdueRepository.sumUnresolvedOverdueAmount()).thenReturn(new BigDecimal("30000000"));
        when(creditLimitRepository.countNormalUsers()).thenReturn(0L);
        when(overdueRepository.countDistinctOverdueUsers()).thenReturn(0L);
        when(creditLimitRepository.countSuspendedUsers()).thenReturn(0L);

        var response = bnplAdminService.getBnplSummary();

        assertThat(response.isOverdueAlert()).isFalse();
        assertThat(response.totalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getBnplUsersNormalizesFiltersAndPage() {
        when(creditLimitRepository.findBnplUsers(
                eq("홍길동"),
                eq("%홍길동%"),
                any(),
                any(),
                eq("OVERDUE"),
                any()
        )).thenReturn(new PageImpl<>(List.of()));

        var response = bnplAdminService.getBnplUsers(null, null, " 홍길동 ", "overdue", 0, 0);

        assertThat(response.pagination().currentPage()).isEqualTo(1);
    }

    @Test
    void getBnplUsersAppliesDefaultStatusAndMaxPageSize() {
        when(creditLimitRepository.findBnplUsers(
                eq(null),
                eq(null),
                any(),
                any(),
                eq("ALL"),
                any()
        )).thenReturn(new PageImpl<>(List.of()));

        var response = bnplAdminService.getBnplUsers(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                " ",
                null,
                2,
                200
        );

        assertThat(response.pagination().currentPage()).isEqualTo(1);
    }

    @Test
    void sendRepaymentAlertSavesNotificationAndAuditLog() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(creditLimitRepository.findActiveByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplCreditLimit(USER_PUBLIC_ID)));
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplUser(USER_PUBLIC_ID, "010-0000-0000")));
        when(notificationRepository.save(any(BnplNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("sms", "상환 안내"),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.channel()).isEqualTo(NotificationChannel.SMS.name());
        org.mockito.Mockito.verify(notificationRepository).save(any(BnplNotification.class));
        org.mockito.Mockito.verify(auditLogRepository).save(any());
    }

    @Test
    void sendRepaymentAlertUsesDefaultMessageWhenCustomMessageIsBlank() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(creditLimitRepository.findActiveByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplCreditLimit(USER_PUBLIC_ID)));
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplUser(USER_PUBLIC_ID, "010-0000-0000")));
        when(notificationRepository.save(any(BnplNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("kakao", " "),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.channel()).isEqualTo(NotificationChannel.KAKAO.name());
    }

    @Test
    void sendRepaymentAlertRejectsMissingUserPhoneAndInvalidChannel() {
        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("email", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);

        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(creditLimitRepository.findActiveByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplCreditLimit(USER_PUBLIC_ID)));
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplUser(USER_PUBLIC_ID, " ")));

        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("sms", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void sendRepaymentAlertRejectsUnsupportedChannelAfterAdminValidation() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));

        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("email", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void sendRepaymentAlertRejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("sms", null),
                null,
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void sendRepaymentAlertRejectsInactiveAdminAndMissingCreditLimit() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "INACTIVE")));

        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("sms", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);

        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(creditLimitRepository.findActiveByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("sms", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void sendRepaymentAlertRejectsMissingUser() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(creditLimitRepository.findActiveByUserPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplCreditLimit(USER_PUBLIC_ID)));
        when(userRepository.findByPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest("sms", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void sendRepaymentAlertRejectsBlankChannelAfterAdminValidation() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));

        assertThatThrownBy(() -> bnplAdminService.sendRepaymentAlert(
                USER_PUBLIC_ID,
                new RepaymentAlertRequest(" ", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void getOverdueSummaryAggregatesStageCounts() {
        when(overdueRepository.countDistinctOverdueUsers()).thenReturn(3L);
        when(overdueRepository.sumUnresolvedOverdueAmount()).thenReturn(new BigDecimal("100000"));
        when(overdueRepository.sumUnresolvedPenaltyAmount()).thenReturn(new BigDecimal("5000"));
        when(overdueRepository.countByStage()).thenReturn(List.of(
                new Object[]{OverdueStage.STAGE_1, 2L},
                new Object[]{OverdueStage.STAGE_3, 1L}
        ));

        var response = bnplAdminService.getOverdueSummary();

        assertThat(response.totalOverdueUsers()).isEqualTo(3);
        assertThat(response.overdueByStage().STAGE_1()).isEqualTo(2);
        assertThat(response.overdueByStage().STAGE_2()).isZero();
        assertThat(response.overdueByStage().STAGE_3()).isEqualTo(1);
    }

    @Test
    void getOverdueUsersRejectsUnsupportedType() {
        assertThatThrownBy(() -> bnplAdminService.getOverdueUsers(null, null, "BAD", null, null, 1, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getOverdueUsersNormalizesFilterTypeDatesAndPaging() {
        when(overdueRepository.findOverdueUsers(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var response = bnplAdminService.getOverdueUsers(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                " principal ",
                OverdueStage.STAGE_2,
                10,
                0,
                200
        );

        assertThat(response.pagination().currentPage()).isEqualTo(1);
    }

    @Test
    void getOverdueUsersTreatsBlankTypeAsAll() {
        when(overdueRepository.findOverdueUsers(eq(null), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var response = bnplAdminService.getOverdueUsers(null, null, " ", null, null, 1, 20);

        assertThat(response.users()).isEmpty();
    }

    @Test
    void sendOverdueAlertsReturnsSuccessAndFailItems() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(bnplUser(USER_PUBLIC_ID, "010-0000-0000")));
        UUID noPhoneUserId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        when(userRepository.findByPublicId(noPhoneUserId))
                .thenReturn(Optional.of(bnplUser(noPhoneUserId, " ")));
        when(notificationRepository.save(any(BnplNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = bnplAdminService.sendOverdueAlerts(
                new OverdueAlertRequest(
                        List.of(USER_PUBLIC_ID.toString(), noPhoneUserId.toString()),
                        "kakao",
                        null
                ),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failCount()).isEqualTo(1);
    }

    @Test
    void sendOverdueAlertsUsesUnresolvedUsersWhenTargetsAreEmpty() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(overdueRepository.findUnresolvedUserPublicIds()).thenReturn(List.of());

        var response = bnplAdminService.sendOverdueAlerts(
                new OverdueAlertRequest(null, "sms", "직접 입력 메시지"),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.totalCount()).isZero();
        assertThat(response.successCount()).isZero();
        assertThat(response.failCount()).isZero();
    }

    @Test
    void sendOverdueAlertsUsesCurrentSecurityContextWhenAuthUserIsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"), null)
        );
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(overdueRepository.findUnresolvedUserPublicIds()).thenReturn(List.of());

        var response = bnplAdminService.sendOverdueAlerts(
                new OverdueAlertRequest(List.of(), "sms", null),
                null,
                "127.0.0.1"
        );

        assertThat(response.totalCount()).isZero();
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendOverdueAlertsRejectsInvalidTargetId() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));

        assertThatThrownBy(() -> bnplAdminService.sendOverdueAlerts(
                new OverdueAlertRequest(List.of("not-a-uuid"), "sms", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void sendOverdueAlertsReturnsFailWhenTransactionStartFails() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenThrow(new RuntimeException("transaction failed"));

        var response = bnplAdminService.sendOverdueAlerts(
                new OverdueAlertRequest(List.of(USER_PUBLIC_ID.toString()), "sms", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.successCount()).isZero();
        assertThat(response.failCount()).isEqualTo(1);
    }

    @Test
    void sendOverdueAlertsReturnsFailWhenUserLookupFailsInsideTransaction() {
        when(adminUserRepository.findByPublicId(ADMIN_PUBLIC_ID))
                .thenReturn(Optional.of(bnplAdminUser(ADMIN_PUBLIC_ID, "ACTIVE")));
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(userRepository.findByPublicId(USER_PUBLIC_ID)).thenThrow(new RuntimeException("repository failed"));

        var response = bnplAdminService.sendOverdueAlerts(
                new OverdueAlertRequest(List.of(USER_PUBLIC_ID.toString()), "sms", null),
                new AuthUserInfo(ADMIN_PUBLIC_ID, "ADMIN"),
                "127.0.0.1"
        );

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.successCount()).isZero();
        assertThat(response.failCount()).isEqualTo(1);
    }
}
