package com.kkpp.core.credit.service;

import com.kkpp.core.credit.domain.AssScore;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.domain.FarmerDocument;
import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.credit.dto.AssScoreResult;
import com.kkpp.core.credit.dto.CreditApplicationDraft;
import com.kkpp.core.credit.dto.UploadedDocument;
import com.kkpp.core.credit.repository.AssScoreRepository;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import com.kkpp.core.credit.repository.FarmerDocumentRepository;
import com.kkpp.core.credit.repository.FarmerProfileRepository;
import com.kkpp.core.auth.exception.UserNotFoundException;
import com.kkpp.core.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    public CreditLimitApplication saveSubmittedApplication(Long userId, CreditApplicationDraft draft,
                                                           List<UploadedDocument> uploadedDocuments) {
        UUID userPublicId = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new)
                .getPublicId();

        FarmerProfile profile = farmerProfileRepository.findByUserPublicId(userPublicId)
                .map(existing -> {
                    existing.update(
                            draft.getAddress(),
                            draft.getAreaSizeM2(),
                            draft.getCropType(),
                            draft.getHasCropInsurance()
                    );
                    return existing;
                })
                .orElseGet(() -> FarmerProfile.create(
                        userPublicId,
                        draft.getAddress(),
                        draft.getAreaSizeM2(),
                        draft.getCropType(),
                        draft.getHasCropInsurance()
                ));
        FarmerProfile savedProfile = farmerProfileRepository.save(profile);

        CreditLimitApplication application = creditLimitApplicationRepository.save(
                CreditLimitApplication.create(userPublicId)
        );

        uploadedDocuments.stream()
                .map(uploaded -> FarmerDocument.create(
                        uploaded.documentType(),
                        uploaded.fileUrl(),
                        application
                ))
                .forEach(farmerDocumentRepository::save);

        saveAssScore(application, savedProfile, draft.getCropType());

        log.info("[CreditSubmit] persisted applicationId={}", application.getPublicId());
        return application;
    }

    private void saveAssScore(CreditLimitApplication application, FarmerProfile profile, CropType cropType) {
        if (assScoreRepository.findByApplication_PublicId(application.getPublicId()).isPresent()) {
            return;
        }

        AssScoreResult scoreResult = assScoringService.calculate(profile, cropType);
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
            if (assScoreRepository.findByApplication_PublicId(application.getPublicId()).isPresent()) {
                return;
            }
            throw exception;
        }
    }
}
