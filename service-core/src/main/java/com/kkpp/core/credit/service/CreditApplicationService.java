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
import com.kkpp.core.global.logging.LogMaskingUtils;
import com.kkpp.core.global.tracing.TracingSupport;
import com.kkpp.core.user.domain.User;
import com.kkpp.core.user.repository.UserRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
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
    private static final String SUBMIT_LOCK_KEY_PREFIX = "credit:application:submit-lock:";
    private static final Duration DRAFT_TTL = Duration.ofHours(1);
    private static final Duration SESSION_MARKER_TTL = Duration.ofDays(7);
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofMinutes(2);
    private static final BigDecimal PYEONG_TO_M2 = new BigDecimal("3.305785");
    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;
    private static final List<String> ALLOWED_FILE_EXTENSIONS = List.of("jpg", "jpeg", "png", "pdf", "heic", "heif");
    private static final List<ApplicationStatus> IN_PROGRESS_STATUSES = List.of(
            ApplicationStatus.PENDING
    );

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final CreditLimitApplicationRepository creditLimitApplicationRepository;
    private final FileStorageService fileStorageService;
    private final CreditSubmitPersistenceService creditSubmitPersistenceService;
    private final TracingSupport tracingSupport;

    public StartSessionResponse startSession(Long userId) {
        UUID userPublicId = resolveUserPublicId(userId);
        if (hasInProgressApplication(userPublicId)) {
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
        long startedAtNanos = System.nanoTime();
        Span span = tracingSupport.startSpan("service-core.credit.submit");
        try (Scope ignored = span.makeCurrent()) {
            return submitWithSpan(userId, sessionId, files, startedAtNanos, span);
        } catch (RuntimeException exception) {
            tracingSupport.recordException(span, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private SubmitResponse submitWithSpan(
            Long userId,
            String sessionId,
            Map<String, MultipartFile> files,
            long startedAtNanos,
            Span span
    ) {
        CreditApplicationDraft draft = getDraft(userId, sessionId, sessionId);
        UUID userPublicId = resolveUserPublicId(userId);
        span.setAttribute("kkpp.event", "credit.application.submit");
        span.setAttribute("kkpp.user.id", userId);
        span.setAttribute("kkpp.user.public_id.masked", LogMaskingUtils.maskIdentifier(userPublicId));
        span.setAttribute("kkpp.session.id.masked", LogMaskingUtils.maskIdentifier(sessionId));

        // 최종 접수 API의 시작 로그입니다. 세션과 사용자 public id는 원문 대신 마스킹해서 남깁니다.
        log.atInfo()
                .addKeyValue("event", "credit.application.submit.started")
                .addKeyValue("userId", userId)
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                .addKeyValue("inputFileKeys", files == null ? "[]" : LogMaskingUtils.summarizeCollection(files.keySet()))
                .addKeyValue("draftCropType", draft.getCropType())
                .addKeyValue("draftHasInsurance", draft.getHasCropInsurance())
                .addKeyValue("draftRequiredDocumentCount", safeSize(draft.getRequiredDocuments()))
                .log("한도 심사 신청 처리를 시작했습니다.");

        if (hasInProgressApplication(userPublicId)) {
            throw new CreditException(CreditErrorCode.APPLICATION_DUPLICATE, userId);
        }

        validateSubmitDraft(draft);

        Map<RequiredDocumentType, MultipartFile> documentFiles = normalizeDocumentFiles(files);
        validateFiles(documentFiles);
        validateRequiredDocuments(draft, documentFiles);
        span.setAttribute("kkpp.document.count", documentFiles.size());

        String lockToken = acquireSubmitLock(userId);
        List<UploadedDocument> uploadedDocuments = List.of();
        try {
            if (hasInProgressApplication(userPublicId)) {
                throw new CreditException(CreditErrorCode.APPLICATION_DUPLICATE, userId);
            }

            uploadedDocuments = uploadDocuments(draft.getSessionId(), documentFiles);
            CreditLimitApplication application = creditSubmitPersistenceService.saveSubmittedApplication(
                    userPublicId,
                    draft,
                    uploadedDocuments
            );
            deleteDraft(draft.getSessionId());
            span.setAttribute("kkpp.application.public_id", application.getPublicId().toString());
            span.setAttribute("kkpp.result.status", "UNDER_REVIEW");
            span.setAttribute("kkpp.duration_ms", elapsedMillis(startedAtNanos));
            log.atInfo()
                    .addKeyValue("event", "credit.application.submit.completed")
                    .addKeyValue("userId", userId)
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                    .addKeyValue("applicationPublicId", application.getPublicId())
                    .addKeyValue("uploadedDocumentCount", uploadedDocuments.size())
                    .addKeyValue("resultStatus", "UNDER_REVIEW")
                    .addKeyValue("durationMs", elapsedMillis(startedAtNanos))
                    .log("한도 심사 신청 처리를 완료했습니다.");
            return new SubmitResponse(application.getPublicId().toString(), "UNDER_REVIEW", "1~3일");
        } catch (RuntimeException exception) {
            span.setAttribute("kkpp.failure_state", "SUBMITTING");
            span.setAttribute("kkpp.duration_ms", elapsedMillis(startedAtNanos));
            if (exception instanceof CreditException creditException) {
                span.setAttribute("kkpp.error.code", creditException.getErrorCode().getCode());
                span.setAttribute("kkpp.error.reason", creditException.getErrorCode().getMessage());
            }
            log.atError()
                    .addKeyValue("event", "credit.application.submit.failed")
                    .addKeyValue("userId", userId)
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                    .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                    .addKeyValue("uploadedDocumentCount", uploadedDocuments.size())
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .addKeyValue("creditErrorCode", creditErrorCode(exception))
                    .addKeyValue("failureReason", failureReason(exception))
                    .addKeyValue("safeInputContext", safeInputContext(exception))
                    .addKeyValue("draftCropType", draft.getCropType())
                    .addKeyValue("draftHasInsurance", draft.getHasCropInsurance())
                    .addKeyValue("draftRequiredDocumentCount", safeSize(draft.getRequiredDocuments()))
                    .addKeyValue("failureState", "SUBMITTING")
                    .addKeyValue("durationMs", elapsedMillis(startedAtNanos))
                    .setCause(exception)
                    .log("한도 심사 신청 처리 중 오류가 발생했습니다.");
            rollbackUploadedDocuments(uploadedDocuments);
            throw exception;
        } finally {
            releaseSubmitLock(userId, lockToken);
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
        try {
            for (Map.Entry<RequiredDocumentType, MultipartFile> entry : files.entrySet()) {
                RequiredDocumentType documentType = entry.getKey();
                MultipartFile file = entry.getValue();
                if (file == null || file.isEmpty()) {
                    continue;
                }
                // 파일명은 개인정보가 섞일 수 있어 남기지 않고, 문서 타입/크기/콘텐츠 타입만 기록합니다.
                log.atInfo()
                        .addKeyValue("event", "credit.document.upload.started")
                        .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                        .addKeyValue("documentType", documentType)
                        .addKeyValue("contentType", file.getContentType())
                        .addKeyValue("fileSize", file.getSize())
                        .log("한도 심사 서류 업로드를 시작했습니다.");
                String fileUrl = fileStorageService.upload(sessionId, file);
                log.atInfo()
                        .addKeyValue("event", "credit.document.upload.completed")
                        .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                        .addKeyValue("documentType", documentType)
                        .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(fileUrl))
                        .log("한도 심사 서류 업로드를 완료했습니다.");
                uploadedDocuments.add(new UploadedDocument(documentType, fileUrl));
            }
            return uploadedDocuments;
        } catch (RuntimeException exception) {
            // 부분 업로드 후 실패한 경우 롤백 대상 개수를 남겨 장애 대응 시 누락 여부를 확인합니다.
            log.atError()
                    .addKeyValue("event", "credit.document.upload.failed")
                    .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                    .addKeyValue("uploadedDocumentCount", uploadedDocuments.size())
                    .addKeyValue("failureState", "UPLOADING_DOCUMENT")
                    .setCause(exception)
                    .log("한도 심사 서류 업로드 중 오류가 발생했습니다.");
            rollbackUploadedDocuments(uploadedDocuments);
            throw exception;
        }
    }

    private void rollbackUploadedDocuments(List<UploadedDocument> uploadedDocuments) {
        uploadedDocuments.forEach(uploadedDocument -> {
            try {
                // 저장소 key는 삭제에 사용하되 로그에는 마스킹된 값만 남깁니다.
                log.atWarn()
                        .addKeyValue("event", "credit.document.rollback.started")
                        .addKeyValue("documentType", uploadedDocument.documentType())
                        .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(uploadedDocument.fileUrl()))
                        .log("한도 심사 서류 업로드 롤백을 시작했습니다.");
                fileStorageService.delete(uploadedDocument.fileUrl());
            } catch (RuntimeException rollbackException) {
                log.atError()
                        .addKeyValue("event", "credit.document.rollback.failed")
                        .addKeyValue("documentType", uploadedDocument.documentType())
                        .addKeyValue("storageKey", LogMaskingUtils.maskStorageKey(uploadedDocument.fileUrl()))
                        .addKeyValue("failureState", "ROLLBACK_DELETE")
                        .setCause(rollbackException)
                        .log("한도 심사 서류 업로드 롤백 삭제에 실패했습니다.");
            }
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

    private String acquireSubmitLock(Long userId) {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(submitLockKey(userId), lockToken, SUBMIT_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new CreditException(CreditErrorCode.APPLICATION_DUPLICATE, userId);
        }
        return lockToken;
    }

    private void releaseSubmitLock(Long userId, String lockToken) {
        String lockKey = submitLockKey(userId);
        Object currentToken = redisTemplate.opsForValue().get(lockKey);
        if (Objects.equals(currentToken, lockToken)) {
            redisTemplate.delete(lockKey);
        }
    }

    private String submitLockKey(Long userId) {
        return SUBMIT_LOCK_KEY_PREFIX + userId;
    }

    private UUID resolveUserPublicId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CreditException(CreditErrorCode.USER_NOT_FOUND, userId));
        return user.getPublicId();
    }

    private boolean hasInProgressApplication(UUID userPublicId) {
        return creditLimitApplicationRepository.existsByUserPublicIdAndStatusIn(
                userPublicId,
                IN_PROGRESS_STATUSES
        );
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private String creditErrorCode(RuntimeException exception) {
        if (exception instanceof CreditException creditException) {
            return creditException.getErrorCode().getCode();
        }
        return null;
    }

    private String failureReason(RuntimeException exception) {
        if (exception instanceof CreditException creditException) {
            return creditException.getErrorCode().getMessage();
        }
        return exception.getMessage();
    }

    private String safeInputContext(RuntimeException exception) {
        if (exception instanceof CreditException creditException) {
            return LogMaskingUtils.describeSafe(creditException.getInputValue());
        }
        return null;
    }
}
