package com.kkpp.core.credit.service;

import com.kkpp.core.credit.domain.ApplicationStatus;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.domain.RequiredDocumentType;
import com.kkpp.core.credit.dto.CreditApplicationDraft;
import com.kkpp.core.credit.dto.UploadedDocument;
import com.kkpp.core.credit.dto.request.CropRequest;
import com.kkpp.core.credit.dto.request.InsuranceRequest;
import com.kkpp.core.credit.dto.request.LandRequest;
import com.kkpp.core.credit.dto.response.InsuranceResponse;
import com.kkpp.core.credit.dto.response.RequiredDocumentResponse;
import com.kkpp.core.credit.dto.response.StartSessionResponse;
import com.kkpp.core.credit.dto.response.SubmitResponse;
import com.kkpp.core.credit.exception.CreditErrorCode;
import com.kkpp.core.credit.exception.CreditException;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditApplicationService {

    private static final String DRAFT_KEY_PREFIX = "credit:application:draft:";
    private static final String SESSION_KEY_PREFIX = "credit:application:session:";
    private static final Duration DRAFT_TTL = Duration.ofHours(1);
    private static final Duration SESSION_MARKER_TTL = Duration.ofDays(7);
    private static final BigDecimal PYEONG_TO_M2 = new BigDecimal("3.305785");
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final List<String> ALLOWED_FILE_EXTENSIONS = List.of("jpg", "jpeg", "png", "pdf");

    private final RedisTemplate<String, Object> redisTemplate;
    private final CreditLimitApplicationRepository creditLimitApplicationRepository;
    private final FileStorageService fileStorageService;
    private final CreditSubmitPersistenceService creditSubmitPersistenceService;

    public StartSessionResponse startSession(Long userId) {
        if (creditLimitApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING)) {
            throw new CreditException(CreditErrorCode.APPLICATION_DUPLICATE, userId);
        }

        String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant expiresAt = Instant.now().plus(DRAFT_TTL);

        CreditApplicationDraft draft = new CreditApplicationDraft();
        draft.setSessionId(sessionId);
        draft.setUserId(userId);

        saveDraft(draft);

        return new StartSessionResponse(sessionId, expiresAt);
    }

    public void saveLand(Long userId, LandRequest request) {
        CreditApplicationDraft draft = getDraft(userId, request.sessionId(), request);

        validateLand(request);

        draft.setAddress(request.address());
        draft.setAreaSizeM2(BigDecimal.valueOf(request.areaSize()).multiply(PYEONG_TO_M2));
        saveDraft(draft);
    }

    public void saveCrop(Long userId, CropRequest request) {
        CreditApplicationDraft draft = getDraft(userId, request.sessionId(), request);
        CropType cropType = CropType.from(request.cropType());

        draft.setCropType(cropType);
        saveDraft(draft);
    }

    public InsuranceResponse saveInsurance(Long userId, InsuranceRequest request) {
        CreditApplicationDraft draft = getDraft(userId, request.sessionId(), request);

        draft.setHasCropInsurance(request.hasInsurance());
        draft.setRequiredDocuments(RequiredDocumentType.byInsurance(request.hasInsurance()));
        saveDraft(draft);

        return new InsuranceResponse(RequiredDocumentType.byInsurance(request.hasInsurance()));
    }

    public SubmitResponse submit(Long userId, String sessionId, Map<String, MultipartFile> files) {
        CreditApplicationDraft draft = getDraft(userId, sessionId, sessionId);

        if (creditLimitApplicationRepository.existsByUserIdAndStatus(userId, ApplicationStatus.PENDING)) {
            throw new CreditException(CreditErrorCode.APPLICATION_DUPLICATE, userId);
        }

        validateSubmitDraft(draft);

        Map<RequiredDocumentType, MultipartFile> documentFiles = normalizeDocumentFiles(files);
        validateFiles(documentFiles);
        validateRequiredDocuments(draft, documentFiles);

        List<UploadedDocument> uploadedDocuments = uploadDocuments(draft.getSessionId(), documentFiles);
        try {
            CreditLimitApplication application = creditSubmitPersistenceService.saveSubmittedApplication(
                    userId,
                    draft,
                    uploadedDocuments
            );
            deleteDraft(draft.getSessionId());
            log.info("[CreditSubmit] completed applicationId={}", application.getPublicId());
            return new SubmitResponse(application.getPublicId().toString(), "UNDER_REVIEW", "1~3일");
        } catch (RuntimeException exception) {
            rollbackUploadedDocuments(uploadedDocuments);
            throw exception;
        }
    }

    private void validateLand(LandRequest request) {
        if (request.address() == null || request.address().isBlank()) {
            throw new CreditException(CreditErrorCode.LAND_INVALID_ADDRESS, request);
        }
        if (request.areaSize() == null || request.areaSize() <= 0) {
            throw new CreditException(CreditErrorCode.LAND_INVALID_AREA_SIZE, request);
        }
        if (isUnsupportedRegion(request.address())) {
            throw new CreditException(CreditErrorCode.LAND_UNSUPPORTED_REGION, request);
        }
    }

    private boolean isUnsupportedRegion(String address) {
        return address.contains("울릉군") || address.contains("백령면") || address.contains("흑산면");
    }

    private void validateSubmitDraft(CreditApplicationDraft draft) {
        if (draft.getAddress() == null || draft.getAddress().isBlank()
                || draft.getAreaSizeM2() == null || draft.getAreaSizeM2().compareTo(BigDecimal.ZERO) <= 0
                || draft.getCropType() == null
                || draft.getHasCropInsurance() == null
                || draft.getRequiredDocuments() == null || draft.getRequiredDocuments().isEmpty()) {
            throw new CreditException(CreditErrorCode.APPLICATION_STEP_MISSING, draft.getSessionId());
        }
    }

    private Map<RequiredDocumentType, MultipartFile> normalizeDocumentFiles(Map<String, MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Map.of();
        }

        java.util.EnumMap<RequiredDocumentType, MultipartFile> documentFiles =
                new java.util.EnumMap<>(RequiredDocumentType.class);
        files.forEach((key, file) -> {
            String documentCode = extractDocumentCode(key);
            RequiredDocumentType documentType = RequiredDocumentType.fromDocumentCode(documentCode);
            documentFiles.put(documentType, file);
        });
        return documentFiles;
    }

    private String extractDocumentCode(String key) {
        if (key != null && key.startsWith("files[") && key.endsWith("]")) {
            return key.substring("files[".length(), key.length() - 1);
        }
        return key;
    }

    private void validateRequiredDocuments(CreditApplicationDraft draft,
                                           Map<RequiredDocumentType, MultipartFile> documentFiles) {
        List<RequiredDocumentResponse> requiredDocuments = draft.getRequiredDocuments();
        if (requiredDocuments == null || requiredDocuments.isEmpty()) {
            requiredDocuments = RequiredDocumentType.byInsurance(Boolean.TRUE.equals(draft.getHasCropInsurance()));
        }

        for (RequiredDocumentResponse requiredDocument : requiredDocuments) {
            if (!requiredDocument.isRequired()) {
                continue;
            }
            RequiredDocumentType documentType = RequiredDocumentType.fromDocumentCode(requiredDocument.documentCode());
            MultipartFile file = documentFiles.get(documentType);
            if (file == null || file.isEmpty()) {
                throw new CreditException(CreditErrorCode.DOCUMENT_REQUIRED_MISSING, requiredDocument.documentCode());
            }
        }
    }

    private void validateFiles(Map<RequiredDocumentType, MultipartFile> documentFiles) {
        documentFiles.forEach((documentType, file) -> {
            if (file == null || file.isEmpty()) {
                return;
            }
            if (!isAllowedFileType(file)) {
                throw new CreditException(CreditErrorCode.DOCUMENT_UNSUPPORTED_TYPE, file.getOriginalFilename());
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new CreditException(CreditErrorCode.DOCUMENT_SIZE_EXCEEDED, file.getOriginalFilename());
            }
        });
    }

    private boolean isAllowedFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            return false;
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return ALLOWED_FILE_EXTENSIONS.contains(extension);
    }

    private List<UploadedDocument> uploadDocuments(String sessionId, Map<RequiredDocumentType, MultipartFile> files) {
        List<UploadedDocument> uploadedDocuments = new ArrayList<>();
        files.forEach((documentType, file) -> {
            if (file == null || file.isEmpty()) {
                return;
            }
            String fileUrl = fileStorageService.upload(sessionId, file);
            log.info("[CreditSubmit] uploaded documentType={} filename={} url={}",
                    documentType,
                    file.getOriginalFilename(),
                    fileUrl);
            uploadedDocuments.add(new UploadedDocument(documentType, fileUrl));
        });
        return uploadedDocuments;
    }

    private void rollbackUploadedDocuments(List<UploadedDocument> uploadedDocuments) {
        uploadedDocuments.forEach(uploadedDocument -> {
            log.error("[CreditSubmit] rolling back uploaded file documentType={} url={}",
                    uploadedDocument.documentType(),
                    uploadedDocument.fileUrl());
            fileStorageService.delete(uploadedDocument.fileUrl());
        });
    }

    private CreditApplicationDraft getDraft(Long userId, String sessionId, Object inputValue) {
        validateSessionId(sessionId, inputValue);

        Object raw = redisTemplate.opsForValue().get(draftKey(sessionId));
        if (raw == null) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(sessionId)))) {
                throw new CreditException(CreditErrorCode.SESSION_EXPIRED, inputValue);
            }
            throw new CreditException(CreditErrorCode.SESSION_NOT_FOUND, inputValue);
        }

        CreditApplicationDraft draft = (CreditApplicationDraft) raw;
        if (!Objects.equals(draft.getUserId(), userId)) {
            throw new CreditException(CreditErrorCode.SESSION_NOT_FOUND, inputValue);
        }
        return draft;
    }

    private void validateSessionId(String sessionId, Object inputValue) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new CreditException(CreditErrorCode.SESSION_ID_REQUIRED, inputValue);
        }
    }

    private void saveDraft(CreditApplicationDraft draft) {
        redisTemplate.opsForValue().set(draftKey(draft.getSessionId()), draft, DRAFT_TTL);
        redisTemplate.opsForValue().set(
                sessionKey(draft.getSessionId()),
                "CREATED",
                DRAFT_TTL.plus(SESSION_MARKER_TTL)
        );
    }

    private String draftKey(String sessionId) {
        return DRAFT_KEY_PREFIX + sessionId;
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private void deleteDraft(String sessionId) {
        redisTemplate.delete(draftKey(sessionId));
        redisTemplate.delete(sessionKey(sessionId));
    }
}
