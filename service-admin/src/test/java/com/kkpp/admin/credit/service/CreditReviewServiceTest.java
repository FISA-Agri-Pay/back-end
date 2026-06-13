package com.kkpp.admin.credit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.kkpp.admin.testsupport.AdminTestEntityFactory.assScore;
import static com.kkpp.admin.testsupport.AdminTestEntityFactory.reviewDocument;

import com.kkpp.admin.credit.domain.CreditReviewApplication;
import com.kkpp.admin.credit.domain.CreditReviewFarmerProfile;
import com.kkpp.admin.credit.domain.CreditReviewLimit;
import com.kkpp.admin.credit.domain.CreditReviewStatus;
import com.kkpp.admin.credit.domain.CreditReviewUser;
import com.kkpp.admin.credit.domain.CreditReviewWallet;
import com.kkpp.admin.credit.dto.ApproveCreditReviewRequest;
import com.kkpp.admin.credit.dto.CreditReviewSummaryResponse;
import com.kkpp.admin.credit.dto.RejectCreditReviewRequest;
import com.kkpp.admin.credit.repository.CreditReviewApplicationRepository;
import com.kkpp.admin.credit.repository.CreditReviewAssScoreRepository;
import com.kkpp.admin.credit.repository.CreditReviewDocumentRepository;
import com.kkpp.admin.credit.repository.CreditReviewFarmerProfileRepository;
import com.kkpp.admin.credit.repository.CreditReviewLimitRepository;
import com.kkpp.admin.credit.repository.CreditReviewWalletRepository;
import com.kkpp.common.core.exception.BusinessException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

    @Test
    void getReviewsReturnsNormalizedPageResponse() {
        CreditReviewSummaryResponse summary = new CreditReviewSummaryResponse(
                APPLICATION_PUBLIC_ID,
                CreditReviewStatus.PENDING,
                "홍길동",
                "010-0000-0000",
                "경기도 안성시",
                new BigDecimal("991.735500"),
                "RICE",
                APPROVED_AMOUNT,
                68,
                false,
                LocalDateTime.now()
        );
        when(applicationRepository.findReviewSummaries(eq(CreditReviewStatus.PENDING), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        java.util.List.of(summary),
                        invocation.getArgument(1),
                        1
                ));

        var response = creditReviewService.getReviews(CreditReviewStatus.PENDING, -1, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(applicationRepository).findReviewSummaries(eq(CreditReviewStatus.PENDING), pageableCaptor.capture());
        Pageable capturedPageable = pageableCaptor.getValue();

        assertThat(response.reviews()).hasSize(1);
        assertThat(capturedPageable.getPageNumber()).isZero();
        assertThat(capturedPageable.getPageSize()).isEqualTo(20);
        assertThat(response.page()).isEqualTo(capturedPageable.getPageNumber());
        assertThat(response.size()).isEqualTo(capturedPageable.getPageSize());
    }

    @Test
    void getReviewReturnsDetailWithOptionalProfileScoreAndDocuments() {
        CreditReviewApplication application = pendingApplication();
        when(applicationRepository.findByPublicId(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));
        when(farmerProfileRepository.findByUser_Id(20L)).thenReturn(Optional.of(farmerProfile()));
        when(assScoreRepository.findByApplication_Id(10L)).thenReturn(Optional.of(assScore(application)));
        when(documentRepository.findAllByApplication_IdOrderByIdAsc(10L))
                .thenReturn(java.util.List.of(reviewDocument(application)));
        when(documentUrlService.resolve("documents/agri.pdf")).thenReturn("https://files/agri.pdf");

        var response = creditReviewService.getReview(APPLICATION_PUBLIC_ID);

        assertThat(response.applicant().userPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(response.farm().fieldAreaPyeong()).isEqualByComparingTo("300.00");
        assertThat(response.ass().totalScore()).isEqualTo(68);
        assertThat(response.documents().getFirst().fileUrl()).isEqualTo("https://files/agri.pdf");
    }

    @Test
    void getReviewReturnsNullOptionalSectionsWhenProfileAndScoreAreMissing() {
        CreditReviewApplication application = pendingApplication();
        when(applicationRepository.findByPublicId(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));
        when(farmerProfileRepository.findByUser_Id(20L)).thenReturn(Optional.empty());
        when(assScoreRepository.findByApplication_Id(10L)).thenReturn(Optional.empty());
        when(documentRepository.findAllByApplication_IdOrderByIdAsc(10L)).thenReturn(java.util.List.of());

        var response = creditReviewService.getReview(APPLICATION_PUBLIC_ID);

        assertThat(response.farm()).isNull();
        assertThat(response.ass().estimatedIncome()).isNull();
        assertThat(response.ass().systemEstimatedLimitAmount()).isEqualByComparingTo(APPROVED_AMOUNT);
        assertThat(response.documents()).isEmpty();
    }

    @Test
    void getReviewThrowsWhenApplicationDoesNotExist() {
        when(applicationRepository.findByPublicId(APPLICATION_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditReviewService.getReview(APPLICATION_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approveRejectsNonPendingApplication() {
        CreditReviewApplication application = pendingApplication();
        set(application, "status", CreditReviewStatus.APPROVED);
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> creditReviewService.approve(APPLICATION_PUBLIC_ID, approveRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approveRejectsAlreadyIssuedLimitAndMissingFarmProfile() {
        CreditReviewApplication alreadyIssuedApplication = pendingApplication();
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID))
                .thenReturn(Optional.of(alreadyIssuedApplication));
        when(limitRepository.existsByApplication_Id(10L)).thenReturn(true);

        assertThatThrownBy(() -> creditReviewService.approve(APPLICATION_PUBLIC_ID, approveRequest()))
                .isInstanceOf(BusinessException.class);

        CreditReviewApplication missingProfileApplication = pendingApplication();
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID))
                .thenReturn(Optional.of(missingProfileApplication));
        when(limitRepository.existsByApplication_Id(10L)).thenReturn(false);
        when(farmerProfileRepository.findByUser_Id(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditReviewService.approve(APPLICATION_PUBLIC_ID, approveRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approveRejectsBlankCropType() {
        CreditReviewApplication application = pendingApplication();
        CreditReviewFarmerProfile profile = farmerProfile();
        set(profile, "mainCrop", " ");
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));
        when(limitRepository.existsByApplication_Id(10L)).thenReturn(false);
        when(farmerProfileRepository.findByUser_Id(20L)).thenReturn(Optional.of(profile));
        when(walletRepository.existsByUserPublicId(USER_PUBLIC_ID)).thenReturn(true);

        assertThatThrownBy(() -> creditReviewService.approve(APPLICATION_PUBLIC_ID, approveRequest()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void approveNormalizesBeanCropTypeAndDefaultApprovalValues() {
        CreditReviewApplication application = pendingApplication();
        CreditReviewFarmerProfile profile = farmerProfile();
        set(profile, "mainCrop", "BEAN");
        ApproveCreditReviewRequest request = new ApproveCreditReviewRequest(
                REVIEWER_PUBLIC_ID,
                APPROVED_AMOUNT,
                null,
                null,
                null
        );
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));
        when(limitRepository.existsByApplication_Id(10L)).thenReturn(false);
        when(farmerProfileRepository.findByUser_Id(20L)).thenReturn(Optional.of(profile));
        when(walletRepository.existsByUserPublicId(USER_PUBLIC_ID)).thenReturn(true);
        when(limitRepository.save(any(CreditReviewLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        creditReviewService.approve(APPLICATION_PUBLIC_ID, request);

        ArgumentCaptor<CreditReviewLimit> limitCaptor = ArgumentCaptor.forClass(CreditReviewLimit.class);
        verify(limitRepository).save(limitCaptor.capture());
        assertThat(limitCaptor.getValue().getCropTypeSnapshot()).isEqualTo("SOYBEAN");
        assertThat(limitCaptor.getValue().getInterestRate()).isEqualByComparingTo("0.0450");
        assertThat(limitCaptor.getValue().getPrincipalDueDate()).isNotNull();
        assertThat(limitCaptor.getValue().getExpiresAt()).isNotNull();
    }

    @Test
    void rejectPendingApplicationWithReasonCodeAndReason() {
        CreditReviewApplication application = pendingApplication();
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));

        var response = creditReviewService.reject(
                APPLICATION_PUBLIC_ID,
                new RejectCreditReviewRequest(REVIEWER_PUBLIC_ID, "DOC_MISSING", "서류 누락")
        );

        assertThat(response.status()).isEqualTo(CreditReviewStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("DOC_MISSING: 서류 누락");
    }

    @Test
    void rejectRequiresReasonAndPendingStatus() {
        CreditReviewApplication application = pendingApplication();
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> creditReviewService.reject(
                APPLICATION_PUBLIC_ID,
                new RejectCreditReviewRequest(REVIEWER_PUBLIC_ID, " ", null)
        )).isInstanceOf(BusinessException.class);

        CreditReviewApplication approved = pendingApplication();
        set(approved, "status", CreditReviewStatus.APPROVED);
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> creditReviewService.reject(
                APPLICATION_PUBLIC_ID,
                new RejectCreditReviewRequest(REVIEWER_PUBLIC_ID, "DOC_MISSING", null)
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectRejectsTooLongReason() {
        CreditReviewApplication application = pendingApplication();
        when(applicationRepository.findByPublicIdForUpdate(APPLICATION_PUBLIC_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> creditReviewService.reject(
                APPLICATION_PUBLIC_ID,
                new RejectCreditReviewRequest(REVIEWER_PUBLIC_ID, null, "가".repeat(501))
        )).isInstanceOf(BusinessException.class);
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
        set(profile, "farmAddress", "경기도 안성시");
        set(profile, "farmZipCode", "17500");
        set(profile, "fieldAreaM2", new BigDecimal("991.735500"));
        set(profile, "hasCropInsurance", true);
        set(profile, "farmingSince", 3);
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
