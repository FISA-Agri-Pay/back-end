package com.kkpp.core.credit.service;

import static com.kkpp.core.testsupport.TestEntityFactory.application;
import static com.kkpp.core.testsupport.TestEntityFactory.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.domain.RequiredDocumentType;
import com.kkpp.core.credit.dto.CreditApplicationDraft;
import com.kkpp.core.credit.dto.request.CropRequest;
import com.kkpp.core.credit.dto.request.InsuranceRequest;
import com.kkpp.core.credit.dto.request.LandRequest;
import com.kkpp.core.credit.dto.response.InsuranceResponse;
import com.kkpp.core.credit.dto.response.StartSessionResponse;
import com.kkpp.core.credit.exception.CreditErrorCode;
import com.kkpp.core.credit.exception.CreditException;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import com.kkpp.core.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class CreditApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SESSION_ID = "sess_123456789abc";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreditLimitApplicationRepository creditLimitApplicationRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private CreditSubmitPersistenceService creditSubmitPersistenceService;

    private CreditApplicationService creditApplicationService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        creditApplicationService = new CreditApplicationService(
                redisTemplate,
                userRepository,
                creditLimitApplicationRepository,
                fileStorageService,
                creditSubmitPersistenceService
        );
    }

    @Test
    void startSessionSavesDraftAndSessionMarkerWhenNoPendingApplicationExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        StartSessionResponse response = creditApplicationService.startSession(USER_ID);

        assertThat(response.sessionId()).startsWith("sess_");
        assertThat(response.expiresAt()).isNotNull();

        ArgumentCaptor<CreditApplicationDraft> draftCaptor = ArgumentCaptor.forClass(CreditApplicationDraft.class);
        verify(valueOperations).set(
                eq("credit:application:draft:" + response.sessionId()),
                draftCaptor.capture(),
                any(Duration.class)
        );
        verify(valueOperations).set(
                eq("credit:application:session:" + response.sessionId()),
                eq("CREATED"),
                any(Duration.class)
        );
        assertThat(draftCaptor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void startSessionThrowsWhenPendingApplicationExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(true);

        assertThatThrownBy(() -> creditApplicationService.startSession(USER_ID))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_DUPLICATE);
    }

    @Test
    void saveLandUpdatesDraftWithConvertedArea() {
        CreditApplicationDraft draft = draftForUser();
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);

        creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, "경기도 안성시 공도읍", 300));

        assertThat(draft.getAddress()).isEqualTo("경기도 안성시 공도읍");
        assertThat(draft.getAreaSizeM2()).isEqualByComparingTo(new BigDecimal("991.735500"));
        verify(valueOperations).set(eq("credit:application:draft:" + SESSION_ID), eq(draft), any(Duration.class));
    }

    @Test
    void saveLandThrowsForUnsupportedRegion() {
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draftForUser());

        assertThatThrownBy(() -> creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, "경북 울릉군", 300)))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.LAND_UNSUPPORTED_REGION);

        assertThatThrownBy(() -> creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, "인천 백령면", 300)))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.LAND_UNSUPPORTED_REGION);

        assertThatThrownBy(() -> creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, "전남 흑산면", 300)))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.LAND_UNSUPPORTED_REGION);
    }

    @Test
    void saveLandThrowsForBlankAddressAndInvalidArea() {
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draftForUser());

        assertThatThrownBy(() -> creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, null, 300)))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.LAND_INVALID_ADDRESS);

        assertThatThrownBy(() -> creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, " ", 300)))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.LAND_INVALID_ADDRESS);

        assertThatThrownBy(() -> creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, "경기도 안성시", null)))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.LAND_INVALID_AREA_SIZE);

        assertThatThrownBy(() -> creditApplicationService.saveLand(USER_ID, new LandRequest(SESSION_ID, "경기도 안성시", 0)))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.LAND_INVALID_AREA_SIZE);
    }

    @Test
    void saveCropUpdatesDraftWithSupportedCropType() {
        CreditApplicationDraft draft = draftForUser();
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);

        creditApplicationService.saveCrop(USER_ID, new CropRequest(SESSION_ID, "RICE"));

        assertThat(draft.getCropType()).isEqualTo(CropType.RICE);
        verify(valueOperations).set(eq("credit:application:draft:" + SESSION_ID), eq(draft), any(Duration.class));
    }

    @Test
    void saveCropThrowsForUnsupportedCropType() {
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draftForUser());

        assertThatThrownBy(() -> creditApplicationService.saveCrop(USER_ID, new CropRequest(SESSION_ID, "APPLE")))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.CROP_UNSUPPORTED_TYPE);
    }

    @Test
    void saveInsuranceReturnsRequiredDocumentsByInsuranceState() {
        CreditApplicationDraft draft = draftForUser();
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);

        InsuranceResponse response = creditApplicationService.saveInsurance(USER_ID, new InsuranceRequest(SESSION_ID, true));

        assertThat(draft.getHasCropInsurance()).isTrue();
        assertThat(response.requiredDocuments())
                .extracting("documentCode")
                .containsExactly(
                        RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION.name(),
                        RequiredDocumentType.CROP_DISASTER_INSURANCE.name()
                );
        assertThat(response.requiredDocuments()).allMatch(document -> document.isRequired());
    }

    @Test
    void submitThrowsWhenRequiredDocumentIsMissing() {
        CreditApplicationDraft draft = completedDraft(true);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.DOCUMENT_REQUIRED_MISSING);

        verify(fileStorageService, never()).upload(anyString(), any(MultipartFile.class));
        verify(creditSubmitPersistenceService, never()).saveSubmittedApplication(any(), any(), anyList());
    }

    @Test
    void submitThrowsWhenDraftStepIsMissing() {
        CreditApplicationDraft missingCrop = completedDraft(false);
        missingCrop.setCropType(null);
        CreditApplicationDraft missingInsurance = completedDraft(false);
        missingInsurance.setHasCropInsurance(null);
        CreditApplicationDraft missingDocuments = completedDraft(false);
        missingDocuments.setRequiredDocuments(null);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID))
                .thenReturn(missingCrop)
                .thenReturn(missingInsurance)
                .thenReturn(missingDocuments);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_STEP_MISSING);
        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_STEP_MISSING);
        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_STEP_MISSING);
    }

    @Test
    void submitThrowsWhenDraftLandFieldsAreMissing() {
        CreditApplicationDraft missingAddress = completedDraft(false);
        missingAddress.setAddress(" ");
        CreditApplicationDraft missingArea = completedDraft(false);
        missingArea.setAreaSizeM2(null);
        CreditApplicationDraft zeroArea = completedDraft(false);
        zeroArea.setAreaSizeM2(BigDecimal.ZERO);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID))
                .thenReturn(missingAddress)
                .thenReturn(missingArea)
                .thenReturn(zeroArea);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_STEP_MISSING);
        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_STEP_MISSING);
        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_STEP_MISSING);
    }

    @Test
    void submitThrowsWhenDraftRequiredDocumentsAreEmpty() {
        CreditApplicationDraft draft = completedDraft(false);
        draft.setRequiredDocuments(List.of());
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of()))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_STEP_MISSING);
    }

    @Test
    void submitThrowsBeforeLockWhenPendingApplicationExists() {
        CreditApplicationDraft draft = completedDraft(false);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(true);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                new MockMultipartFile("AGRI_MANAGEMENT_REGISTRATION", "agri.pdf", "application/pdf", "ok".getBytes())
        )))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_DUPLICATE);
    }

    @Test
    void submitThrowsForUnsupportedAndOversizedFiles() {
        CreditApplicationDraft draft = completedDraft(false);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                new MockMultipartFile("AGRI_MANAGEMENT_REGISTRATION", "agri.exe", "application/octet-stream", "ok".getBytes())
        )))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.DOCUMENT_UNSUPPORTED_TYPE);

        MultipartFile oversizedFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(oversizedFile.isEmpty()).thenReturn(false);
        when(oversizedFile.getOriginalFilename()).thenReturn("agri.pdf");
        when(oversizedFile.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                oversizedFile
        )))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.DOCUMENT_SIZE_EXCEEDED);

        MultipartFile noNameFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(noNameFile.isEmpty()).thenReturn(false);
        when(noNameFile.getOriginalFilename()).thenReturn(null);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                noNameFile
        )))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.DOCUMENT_UNSUPPORTED_TYPE);
    }

    @Test
    void submitAcceptsNullFileMapAsMissingDocumentAndNullDocumentKeyAsInvalidDocument() {
        CreditApplicationDraft draft = completedDraft(false);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(draft);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, null))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.DOCUMENT_REQUIRED_MISSING);

        Map<String, MultipartFile> files = new HashMap<>();
        files.put(null, new MockMultipartFile("file", "agri.pdf", "application/pdf", "ok".getBytes()));

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, files))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.DOCUMENT_REQUIRED_MISSING);
    }

    @Test
    void submitThrowsWhenSessionIsMissingExpiredOrOwnedByAnotherUser() {
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(null);
        when(redisTemplate.hasKey("credit:application:session:" + SESSION_ID)).thenReturn(true);

        assertThatThrownBy(() -> creditApplicationService.saveCrop(USER_ID, new CropRequest(SESSION_ID, "RICE")))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.SESSION_EXPIRED);

        when(redisTemplate.hasKey("credit:application:session:" + SESSION_ID)).thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.saveCrop(USER_ID, new CropRequest(SESSION_ID, "RICE")))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.SESSION_NOT_FOUND);

        CreditApplicationDraft otherUserDraft = draftForUser();
        otherUserDraft.setUserId(999L);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenReturn(otherUserDraft);

        assertThatThrownBy(() -> creditApplicationService.saveCrop(USER_ID, new CropRequest(SESSION_ID, "RICE")))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void submitThrowsWhenSessionIdIsBlank() {
        assertThatThrownBy(() -> creditApplicationService.saveCrop(USER_ID, new CropRequest(" ", "RICE")))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.SESSION_ID_REQUIRED);

        assertThatThrownBy(() -> creditApplicationService.saveCrop(USER_ID, new CropRequest(null, "RICE")))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.SESSION_ID_REQUIRED);
    }

    @Test
    void submitUploadsDocumentsPersistsApplicationAndDeletesDraft() {
        CreditApplicationDraft draft = completedDraft(true);
        AtomicReference<String> lockToken = new AtomicReference<>();
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenAnswer(invocation -> draft);
        when(valueOperations.get("credit:application:submit-lock:" + USER_ID)).thenAnswer(invocation -> lockToken.get());
        when(valueOperations.setIfAbsent(eq("credit:application:submit-lock:" + USER_ID), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    lockToken.set(invocation.getArgument(1));
                    return true;
                });
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);
        when(fileStorageService.upload(eq(SESSION_ID), any(MultipartFile.class)))
                .thenReturn("https://files/agri.pdf", "https://files/insurance.png");
        when(creditSubmitPersistenceService.saveSubmittedApplication(eq(USER_PUBLIC_ID), eq(draft), anyList()))
                .thenReturn(application(USER_PUBLIC_ID, ApplicationStatus.PENDING));

        var response = creditApplicationService.submit(USER_ID, SESSION_ID, requiredFiles());

        assertThat(response.status()).isEqualTo("UNDER_REVIEW");
        verify(redisTemplate).delete("credit:application:draft:" + SESSION_ID);
        verify(redisTemplate).delete("credit:application:session:" + SESSION_ID);
        verify(redisTemplate).delete("credit:application:submit-lock:" + USER_ID);
        verify(fileStorageService, never()).delete(anyString());
    }

    @Test
    void submitSkipsEmptyOptionalDocumentFile() {
        CreditApplicationDraft draft = completedDraft(false);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenAnswer(invocation -> draft);
        when(valueOperations.get("credit:application:submit-lock:" + USER_ID)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("credit:application:submit-lock:" + USER_ID), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);
        when(fileStorageService.upload(eq(SESSION_ID), any(MultipartFile.class))).thenReturn("https://files/agri.pdf");
        when(creditSubmitPersistenceService.saveSubmittedApplication(eq(USER_PUBLIC_ID), eq(draft), anyList()))
                .thenReturn(application(USER_PUBLIC_ID, ApplicationStatus.PENDING));

        creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                new MockMultipartFile("AGRI_MANAGEMENT_REGISTRATION", "agri.pdf", "application/pdf", "ok".getBytes()),
                "CROP_DISASTER_INSURANCE",
                new MockMultipartFile("CROP_DISASTER_INSURANCE", "insurance.pdf", "application/pdf", new byte[0])
        ));

        verify(fileStorageService).upload(eq(SESSION_ID), any(MultipartFile.class));
    }

    @Test
    void submitThrowsWhenSubmitLockAlreadyExists() {
        CreditApplicationDraft draft = completedDraft(false);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenAnswer(invocation -> draft);
        when(valueOperations.setIfAbsent(eq("credit:application:submit-lock:" + USER_ID), anyString(), any(Duration.class)))
                .thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                new MockMultipartFile("AGRI_MANAGEMENT_REGISTRATION", "agri.pdf", "application/pdf", "ok".getBytes())
        )))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_DUPLICATE);
    }

    @Test
    void submitThrowsAndReleasesLockWhenDuplicateAppearsAfterLock() {
        CreditApplicationDraft draft = completedDraft(false);
        AtomicReference<String> lockToken = new AtomicReference<>();
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenAnswer(invocation -> draft);
        when(valueOperations.get("credit:application:submit-lock:" + USER_ID)).thenAnswer(invocation -> lockToken.get());
        when(valueOperations.setIfAbsent(eq("credit:application:submit-lock:" + USER_ID), anyString(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    lockToken.set(invocation.getArgument(1));
                    return true;
                });
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false, true);

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                new MockMultipartFile("AGRI_MANAGEMENT_REGISTRATION", "agri.pdf", "application/pdf", "ok".getBytes())
        )))
                .isInstanceOf(CreditException.class)
                .extracting("errorCode")
                .isEqualTo(CreditErrorCode.APPLICATION_DUPLICATE);

        verify(redisTemplate).delete("credit:application:submit-lock:" + USER_ID);
    }

    @Test
    void submitRollsBackUploadedDocumentsWhenPersistenceFails() {
        CreditApplicationDraft draft = completedDraft(false);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenAnswer(invocation -> draft);
        when(valueOperations.get("credit:application:submit-lock:" + USER_ID)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("credit:application:submit-lock:" + USER_ID), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);
        when(fileStorageService.upload(eq(SESSION_ID), any(MultipartFile.class)))
                .thenReturn("https://files/agri.pdf");
        when(creditSubmitPersistenceService.saveSubmittedApplication(eq(USER_PUBLIC_ID), eq(draft), anyList()))
                .thenThrow(new IllegalStateException("DB 저장 실패"));

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "files[AGRI_MANAGEMENT_REGISTRATION]",
                new MockMultipartFile("files[AGRI_MANAGEMENT_REGISTRATION]", "agri.pdf", "application/pdf", "ok".getBytes())
        ))).isInstanceOf(IllegalStateException.class);

        verify(fileStorageService).delete("https://files/agri.pdf");
    }

    @Test
    void submitIgnoresRollbackDeleteFailure() {
        CreditApplicationDraft draft = completedDraft(false);
        when(valueOperations.get("credit:application:draft:" + SESSION_ID)).thenAnswer(invocation -> draft);
        when(valueOperations.get("credit:application:submit-lock:" + USER_ID)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("credit:application:submit-lock:" + USER_ID), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, USER_PUBLIC_ID, "홍길동")));
        when(creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(eq(USER_PUBLIC_ID), anyList()))
                .thenReturn(false);
        when(fileStorageService.upload(eq(SESSION_ID), any(MultipartFile.class)))
                .thenReturn("https://files/agri.pdf");
        when(creditSubmitPersistenceService.saveSubmittedApplication(eq(USER_PUBLIC_ID), eq(draft), anyList()))
                .thenThrow(new IllegalStateException("DB 저장 실패"));
        org.mockito.Mockito.doThrow(new IllegalStateException("삭제 실패"))
                .when(fileStorageService).delete("https://files/agri.pdf");

        assertThatThrownBy(() -> creditApplicationService.submit(USER_ID, SESSION_ID, Map.of(
                "AGRI_MANAGEMENT_REGISTRATION",
                new MockMultipartFile("AGRI_MANAGEMENT_REGISTRATION", "agri.pdf", "application/pdf", "ok".getBytes())
        ))).isInstanceOf(IllegalStateException.class);

        verify(fileStorageService).delete("https://files/agri.pdf");
    }

    private CreditApplicationDraft draftForUser() {
        CreditApplicationDraft draft = new CreditApplicationDraft();
        draft.setSessionId(SESSION_ID);
        draft.setUserId(USER_ID);
        return draft;
    }

    private CreditApplicationDraft completedDraft(boolean hasInsurance) {
        CreditApplicationDraft draft = draftForUser();
        draft.setAddress("경기도 안성시 공도읍");
        draft.setAreaSizeM2(new BigDecimal("991.735500"));
        draft.setCropType(CropType.RICE);
        draft.setHasCropInsurance(hasInsurance);
        draft.setRequiredDocuments(RequiredDocumentType.byInsurance(hasInsurance));
        return draft;
    }

    private Map<String, MultipartFile> requiredFiles() {
        return Map.of(
                "files[AGRI_MANAGEMENT_REGISTRATION]",
                new MockMultipartFile("files[AGRI_MANAGEMENT_REGISTRATION]", "agri.pdf", "application/pdf", "ok".getBytes()),
                "files[CROP_DISASTER_INSURANCE]",
                new MockMultipartFile("files[CROP_DISASTER_INSURANCE]", "insurance.png", "image/png", "ok".getBytes())
        );
    }
}
