package com.kkpp.admin.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.admin.credit.domain.CreditReviewApplication;
import com.kkpp.admin.credit.domain.CreditReviewFarmerProfile;
import com.kkpp.admin.credit.domain.CreditReviewLimit;
import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.domain.CreditReviewUser;
import com.kkpp.admin.credit.domain.CreditReviewWallet;
import com.kkpp.admin.credit.dto.ApproveCreditReviewRequest;
import com.kkpp.admin.credit.repository.CreditReviewApplicationRepository;
import com.kkpp.admin.credit.repository.CreditReviewAssScoreRepository;
import com.kkpp.admin.credit.repository.CreditReviewDocumentRepository;
import com.kkpp.admin.credit.repository.CreditReviewFarmerProfileRepository;
import com.kkpp.admin.credit.repository.CreditReviewLimitRepository;
import com.kkpp.admin.credit.repository.CreditReviewWalletRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditReviewServiceTest {

    private static final UUID APPLICATION_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID USER_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID REVIEWER_PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final BigDecimal APPROVED_AMOUNT = new BigDecimal("1000000.00");

    @Mock
    private CreditReviewApplicationRepository applicationRepository;

    @Mock
    private CreditReviewFarmerProfileRepository farmerProfileRepository;

    @Mock
    private CreditReviewDocumentRepository documentRepository;

    @Mock
    private CreditReviewAssScoreRepository assScoreRepository;

    @Mock
    private CreditReviewLimitRepository limitRepository;

    @Mock
    private CreditReviewWalletRepository walletRepository;

    @Mock
    private DocumentUrlService documentUrlService;

    private CreditReviewService creditReviewService;

    @BeforeEach
    void setUp() {
        creditReviewService = new CreditReviewService(
                applicationRepository,
                farmerProfileRepository,
                documentRepository,
                assScoreRepository,
                limitRepository,
                walletRepository,
                documentUrlService
        );
    }

    @Test
    void approveCreatesWalletWhenUserDoesNotHaveWallet() {
        CreditReviewApplication application = pendingApplication();
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));
        when(limitRepository.existsByApplication_Id(10L)).thenReturn(false);
        when(farmerProfileRepository.findByUser_Id(20L)).thenReturn(Optional.of(farmerProfile()));
        when(walletRepository.existsByUserPublicId(USER_PUBLIC_ID)).thenReturn(false);
        when(walletRepository.insertWalletIfAbsent(
                any(UUID.class),
                eq(USER_PUBLIC_ID),
                any(BigDecimal.class),
                eq("local-bank"),
                eq("KKPP-" + USER_PUBLIC_ID.toString().replace("-", "")),
                eq(CreditReviewWallet.STATUS_ACTIVE)
        )).thenReturn(1);
        when(limitRepository.save(any(CreditReviewLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        creditReviewService.approve(APPLICATION_PUBLIC_ID, approveRequest());

        ArgumentCaptor<UUID> walletPublicIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<BigDecimal> balanceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(walletRepository).insertWalletIfAbsent(
                walletPublicIdCaptor.capture(),
                eq(USER_PUBLIC_ID),
                balanceCaptor.capture(),
                eq("local-bank"),
                eq("KKPP-" + USER_PUBLIC_ID.toString().replace("-", "")),
                eq(CreditReviewWallet.STATUS_ACTIVE)
        );
        assertThat(walletPublicIdCaptor.getValue()).isNotNull();
        assertThat(balanceCaptor.getValue()).isEqualByComparingTo(BigDecimal.ZERO);

        ArgumentCaptor<CreditReviewLimit> limitCaptor = ArgumentCaptor.forClass(CreditReviewLimit.class);
        verify(limitRepository).save(limitCaptor.capture());
        assertThat(limitCaptor.getValue().getCropTypeSnapshot()).isEqualTo("RICE");
        assertThat(limitCaptor.getValue().getInterestDueDay())
                .isEqualTo(Math.min(LocalDate.now().getDayOfMonth() + 10, 28));
    }

    @Test
    void approveKeepsExistingWalletWithoutOverwritingIt() {
        CreditReviewApplication application = pendingApplication();
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));
        when(limitRepository.existsByApplication_Id(10L)).thenReturn(false);
        when(farmerProfileRepository.findByUser_Id(20L)).thenReturn(Optional.of(farmerProfile()));
        when(walletRepository.existsByUserPublicId(USER_PUBLIC_ID)).thenReturn(true);
        when(limitRepository.save(any(CreditReviewLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        creditReviewService.approve(APPLICATION_PUBLIC_ID, approveRequest());

        verify(walletRepository, never()).insertWalletIfAbsent(
                any(UUID.class),
                any(UUID.class),
                any(BigDecimal.class),
                any(String.class),
                any(String.class),
                any(String.class)
        );
    }

    private ApproveCreditReviewRequest approveRequest() {
        return new ApproveCreditReviewRequest(
                REVIEWER_PUBLIC_ID,
                APPROVED_AMOUNT,
                new BigDecimal("0.0450"),
                LocalDate.now().plusMonths(8),
                LocalDate.now().plusYears(1)
        );
    }

    private CreditReviewApplication pendingApplication() {
        CreditReviewUser user = instantiate(CreditReviewUser.class);
        set(user, "id", 20L);
        set(user, "publicId", USER_PUBLIC_ID);
        set(user, "name", "tester");
        set(user, "phone", "010-0000-0000");
        set(user, "residentIdHash", "hash");
        set(user, "address", "address");
        set(user, "zipCode", "00000");
        set(user, "status", "ACTIVE");

        CreditReviewApplication application = instantiate(CreditReviewApplication.class);
        set(application, "id", 10L);
        set(application, "publicId", APPLICATION_PUBLIC_ID);
        set(application, "user", user);
        set(application, "requestedAmount", APPROVED_AMOUNT);
        set(application, "reapplication", false);
        set(application, "status", CreditReviewStatus.PENDING);
        set(application, "appliedAt", LocalDateTime.now());
        return application;
    }

    private CreditReviewFarmerProfile farmerProfile() {
        CreditReviewFarmerProfile profile = instantiate(CreditReviewFarmerProfile.class);
        set(profile, "id", 30L);
        set(profile, "mainCrop", "RICE");
        return profile;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate test entity: " + type.getName(), exception);
        }
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to set test field: " + fieldName, exception);
        }
    }
}
