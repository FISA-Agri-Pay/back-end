package com.kkpp.core.credit.service;

import com.kkpp.core.credit.domain.AssScore;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import com.kkpp.core.credit.domain.FarmerDocument;
import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.credit.dto.AssScoreResult;
import com.kkpp.core.credit.dto.CreditApplicationDraft;
import com.kkpp.core.credit.dto.UploadedDocument;
import com.kkpp.core.credit.exception.CreditErrorCode;
import com.kkpp.core.credit.exception.CreditException;
import com.kkpp.core.credit.repository.AssScoreRepository;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import com.kkpp.core.credit.repository.FarmerDocumentRepository;
import com.kkpp.core.credit.repository.FarmerProfileRepository;
import com.kkpp.core.global.logging.LogMaskingUtils;
import com.kkpp.core.user.domain.User;
import com.kkpp.core.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditSubmitPersistenceService {

    private final FarmerProfileRepository farmerProfileRepository;
    private final FarmerDocumentRepository farmerDocumentRepository;
    private final CreditLimitApplicationRepository creditLimitApplicationRepository;
    private final AssScoreRepository assScoreRepository;
    private final AssScoringService assScoringService;
    private final UserRepository userRepository;

    @Transactional
    public CreditLimitApplication saveSubmittedApplication(
            UUID userPublicId,
            CreditApplicationDraft draft,
            List<UploadedDocument> uploadedDocuments
    ) {
        // 최종 접수 트랜잭션의 DB 저장 구간을 별도 이벤트로 남겨 업로드/저장 병목을 구분합니다.
        log.atInfo()
                .addKeyValue("event", "credit.application.persistence.started")
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(draft.getSessionId()))
                .addKeyValue("cropType", draft.getCropType())
                .addKeyValue("documentCount", uploadedDocuments.size())
                .log("한도 심사 신청 저장을 시작했습니다.");

        try {
            User user = userRepository.findByPublicId(userPublicId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userPublicId=" + userPublicId));

            FarmerProfile profile = farmerProfileRepository.findByUserPublicId(userPublicId)
                    .map(existing -> {
                        existing.update(
                                draft.getAddress(),
                                user.getAddressDetail(),
                                user.getZipCode(),
                                draft.getAreaSizeM2(),
                                draft.getCropType(),
                                draft.getHasCropInsurance()
                        );
                        return existing;
                    })
                    .orElseGet(() -> FarmerProfile.create(
                            userPublicId,
                            draft.getAddress(),
                            user.getAddressDetail(),
                            user.getZipCode(),
                            draft.getAreaSizeM2(),
                            draft.getCropType(),
                            draft.getHasCropInsurance()
                    ));
            FarmerProfile savedProfile = farmerProfileRepository.save(profile);
            AssScoreResult scoreResult = assScoringService.calculate(savedProfile, draft.getCropType());

            CreditLimitApplication application;
            try {
                application = creditLimitApplicationRepository.save(
                        CreditLimitApplication.create(userPublicId, requestedAmount(scoreResult))
                );
            } catch (DataIntegrityViolationException exception) {
                throw new CreditException(CreditErrorCode.APPLICATION_DUPLICATE, userPublicId);
            }

            uploadedDocuments.stream()
                    .map(uploaded -> FarmerDocument.create(
                            userPublicId,
                            application.getPublicId(),
                            uploaded.documentType(),
                            uploaded.fileUrl()
                    ))
                    .forEach(farmerDocumentRepository::save);

            saveAssScore(application, scoreResult);

            // 금액 상세 대신 문서 개수와 총점만 남겨 심사 결과 흐름을 추적합니다.
            log.atInfo()
                    .addKeyValue("event", "credit.application.persistence.completed")
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                    .addKeyValue("applicationPublicId", application.getPublicId())
                    .addKeyValue("documentCount", uploadedDocuments.size())
                    .addKeyValue("totalScore", scoreResult.totalScore())
                    .log("한도 심사 신청과 평가 점수 저장을 완료했습니다.");
            return application;
        } catch (RuntimeException exception) {
            // 트랜잭션 롤백 원인을 빠르게 찾을 수 있도록 실패 구간을 고정된 코드로 남깁니다.
            log.atError()
                    .addKeyValue("event", "credit.application.persistence.failed")
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(userPublicId))
                    .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(draft.getSessionId()))
                    .addKeyValue("documentCount", uploadedDocuments.size())
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .addKeyValue("creditErrorCode", creditErrorCode(exception))
                    .addKeyValue("failureReason", failureReason(exception))
                    .addKeyValue("cropType", draft.getCropType())
                    .addKeyValue("hasInsurance", draft.getHasCropInsurance())
                    .addKeyValue("failureState", "PERSISTING_APPLICATION")
                    .setCause(exception)
                    .log("한도 심사 신청 저장 중 오류가 발생했습니다.");
            throw exception;
        }
    }

    private void saveAssScore(CreditLimitApplication application, AssScoreResult scoreResult) {
        if (assScoreRepository.findByApplicationPublicId(application.getPublicId()).isPresent()) {
            return;
        }

        try {
            assScoreRepository.saveAndFlush(AssScore.create(
                    application,
                    scoreResult.estimatedIncome(),
                    scoreResult.priceSnapshotDate(),
                    scoreResult.incomeScore(),
                    scoreResult.insuranceScore(),
                    scoreResult.farmingCareerScore(),
                    scoreResult.totalScore(),
                    scoreResult.calculatedAt()
            ));
        } catch (DataIntegrityViolationException exception) {
            if (assScoreRepository.findByApplicationPublicId(application.getPublicId()).isPresent()) {
                return;
            }
            throw exception;
        }
    }

    private BigDecimal requestedAmount(AssScoreResult scoreResult) {
        return scoreResult.estimatedIncome().max(BigDecimal.ONE);
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
}
