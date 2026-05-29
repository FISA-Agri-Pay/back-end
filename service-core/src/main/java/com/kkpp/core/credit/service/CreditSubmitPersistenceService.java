package com.kkpp.core.credit.service;

import com.kkpp.core.auth.domain.User;
import com.kkpp.core.auth.repository.UserRepository;
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

        log.info("한도 심사 신청과 평가 점수 저장을 완료했습니다. applicationPublicId={}", application.getPublicId());
        return application;
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
}
