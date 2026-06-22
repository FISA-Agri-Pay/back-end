package com.kkpp.core.credit.service;

import static com.kkpp.core.testsupport.TestEntityFactory.farmerProfile;
import static com.kkpp.core.testsupport.TestEntityFactory.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.domain.AssScore;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.domain.FarmerDocument;
import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.credit.domain.RequiredDocumentType;
import com.kkpp.core.credit.dto.AssScoreResult;
import com.kkpp.core.credit.dto.CreditApplicationDraft;
import com.kkpp.core.credit.dto.UploadedDocument;
import com.kkpp.core.credit.exception.CreditErrorCode;
import com.kkpp.core.credit.exception.CreditException;
import com.kkpp.core.credit.repository.AssScoreRepository;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import com.kkpp.core.credit.repository.FarmerDocumentRepository;
import com.kkpp.core.credit.repository.FarmerProfileRepository;
import com.kkpp.core.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CreditSubmitPersistenceServiceTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private FarmerProfileRepository farmerProfileRepository;

    @Mock
    private FarmerDocumentRepository farmerDocumentRepository;

    @Mock
    private CreditLimitApplicationRepository creditLimitApplicationRepository;

    @Mock
    private AssScoreRepository assScoreRepository;

    @Mock
    private AssScoringService assScoringService;

    @Mock
    private UserRepository userRepository;

    private CreditSubmitPersistenceService creditSubmitPersistenceService;

    @BeforeEach
    void setUp() {
        creditSubmitPersistenceService = new CreditSubmitPersistenceService(
                farmerProfileRepository,
                farmerDocumentRepository,
                creditLimitApplicationRepository,
                assScoreRepository,
                assScoringService,
                userRepository
        );
    }

    @Test
    void saveSubmittedApplicationCreatesProfileApplicationDocumentsAndAssScore() {
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user(1L, USER_PUBLIC_ID, "홍길동")));
        when(farmerProfileRepository.findByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());
        when(farmerProfileRepository.save(any(FarmerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoringService.calculate(any(FarmerProfile.class), any(CropType.class))).thenReturn(scoreResult());
        when(creditLimitApplicationRepository.save(any(CreditLimitApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoreRepository.findByApplicationPublicId(any(UUID.class))).thenReturn(Optional.empty());
        when(assScoreRepository.saveAndFlush(any(AssScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditLimitApplication application = creditSubmitPersistenceService.saveSubmittedApplication(
                USER_PUBLIC_ID,
                completedDraft(),
                List.of(
                        new UploadedDocument(RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION, "https://files/agri.pdf"),
                        new UploadedDocument(RequiredDocumentType.CROP_DISASTER_INSURANCE, "https://files/insurance.pdf")
                )
        );

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.getRequestedAmount()).isEqualByComparingTo("15000000");
        verify(farmerDocumentRepository, times(2)).save(any(FarmerDocument.class));
        verify(assScoreRepository).saveAndFlush(any(AssScore.class));
    }

    @Test
    void saveSubmittedApplicationUpdatesExistingProfileAndSkipsAssScoreWhenAlreadyExists() {
        FarmerProfile existingProfile = farmerProfile(new BigDecimal("1000"), CropType.RICE, false);
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user(1L, USER_PUBLIC_ID, "홍길동")));
        when(farmerProfileRepository.findByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.of(existingProfile));
        when(farmerProfileRepository.save(existingProfile)).thenReturn(existingProfile);
        when(assScoringService.calculate(existingProfile, CropType.RICE)).thenReturn(scoreResult());
        when(creditLimitApplicationRepository.save(any(CreditLimitApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoreRepository.findByApplicationPublicId(any(UUID.class)))
                .thenReturn(Optional.of(AssScore.create(
                        CreditLimitApplication.create(USER_PUBLIC_ID, BigDecimal.ONE),
                        BigDecimal.ONE,
                        LocalDate.now(),
                        1,
                        1,
                        1,
                        3,
                        LocalDateTime.now()
                )));

        creditSubmitPersistenceService.saveSubmittedApplication(
                USER_PUBLIC_ID,
                completedDraft(),
                List.of(new UploadedDocument(RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION, "https://files/agri.pdf"))
        );

        assertThat(existingProfile.getFarmAddress()).isEqualTo("경기도 안성시 공도읍");
        assertThat(existingProfile.getFieldAreaM2()).isEqualByComparingTo("991.735500");
        assertThat(existingProfile.getHasCropInsurance()).isTrue();
    }

    @Test
    void saveSubmittedApplicationWrapsDuplicateApplicationAsCreditException() {
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user(1L, USER_PUBLIC_ID, "홍길동")));
        when(farmerProfileRepository.findByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());
        when(farmerProfileRepository.save(any(FarmerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoringService.calculate(any(FarmerProfile.class), any(CropType.class))).thenReturn(scoreResult());
        when(creditLimitApplicationRepository.save(any(CreditLimitApplication.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> creditSubmitPersistenceService.saveSubmittedApplication(
                USER_PUBLIC_ID,
                completedDraft(),
                List.of()
        ))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_DUPLICATE);
    }

    @Test
    void saveSubmittedApplicationPersistsRequestedAmountAtLeastOneWon() {
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user(1L, USER_PUBLIC_ID, "홍길동")));
        when(farmerProfileRepository.findByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());
        when(farmerProfileRepository.save(any(FarmerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoringService.calculate(any(FarmerProfile.class), any(CropType.class)))
                .thenReturn(new AssScoreResult(
                        BigDecimal.ZERO,
                        LocalDate.now(),
                        0,
                        0,
                        0,
                        0,
                        LocalDateTime.now()
                ));
        when(creditLimitApplicationRepository.save(any(CreditLimitApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoreRepository.findByApplicationPublicId(any(UUID.class))).thenReturn(Optional.empty());
        when(assScoreRepository.saveAndFlush(any(AssScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<CreditLimitApplication> applicationCaptor = ArgumentCaptor.forClass(CreditLimitApplication.class);

        creditSubmitPersistenceService.saveSubmittedApplication(USER_PUBLIC_ID, completedDraft(), List.of());

        verify(creditLimitApplicationRepository).save(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getRequestedAmount()).isEqualByComparingTo("1");
    }

    @Test
    void saveSubmittedApplicationIgnoresAssScoreDuplicateRaceWhenScoreAlreadyExists() {
        when(userRepository.findByPublicId(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user(1L, USER_PUBLIC_ID, "홍길동")));
        when(farmerProfileRepository.findByUserPublicId(USER_PUBLIC_ID)).thenReturn(Optional.empty());
        when(farmerProfileRepository.save(any(FarmerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoringService.calculate(any(FarmerProfile.class), any(CropType.class))).thenReturn(scoreResult());
        when(creditLimitApplicationRepository.save(any(CreditLimitApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assScoreRepository.findByApplicationPublicId(any(UUID.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(AssScore.create(
                        CreditLimitApplication.create(USER_PUBLIC_ID, BigDecimal.ONE),
                        BigDecimal.ONE,
                        LocalDate.now(),
                        1,
                        1,
                        1,
                        3,
                        LocalDateTime.now()
                )));
        when(assScoreRepository.saveAndFlush(any(AssScore.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate ass score"));

        CreditLimitApplication application = creditSubmitPersistenceService.saveSubmittedApplication(
                USER_PUBLIC_ID,
                completedDraft(),
                List.of()
        );

        assertThat(application.getPublicId()).isNotNull();
        verify(assScoreRepository).saveAndFlush(any(AssScore.class));
    }

    private CreditApplicationDraft completedDraft() {
        CreditApplicationDraft draft = new CreditApplicationDraft();
        draft.setSessionId("sess_123456789abc");
        draft.setUserId(1L);
        draft.setAddress("경기도 안성시 공도읍");
        draft.setAreaSizeM2(new BigDecimal("991.735500"));
        draft.setCropType(CropType.RICE);
        draft.setHasCropInsurance(true);
        draft.setRequiredDocuments(RequiredDocumentType.byInsurance(true));
        return draft;
    }

    private AssScoreResult scoreResult() {
        return new AssScoreResult(
                new BigDecimal("15000000"),
                LocalDate.now(),
                36,
                25,
                7,
                68,
                LocalDateTime.now()
        );
    }
}
