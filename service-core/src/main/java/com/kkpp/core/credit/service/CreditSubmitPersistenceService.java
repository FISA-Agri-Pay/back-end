package com.kkpp.core.credit.service;

import com.kkpp.core.credit.domain.AssScore;
import com.kkpp.core.credit.domain.CreditLimitApplication;
import com.kkpp.core.credit.domain.FarmerDocument;
import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.credit.dto.AssScoreResult;
import com.kkpp.core.credit.dto.CreditApplicationDraft;
import com.kkpp.core.credit.dto.UploadedDocument;
import com.kkpp.core.credit.repository.AssScoreRepository;
import com.kkpp.core.credit.repository.CreditLimitApplicationRepository;
import com.kkpp.core.credit.repository.FarmerDocumentRepository;
import com.kkpp.core.credit.repository.FarmerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditSubmitPersistenceService {

    private final FarmerProfileRepository farmerProfileRepository;
    private final FarmerDocumentRepository farmerDocumentRepository;
    private final CreditLimitApplicationRepository creditLimitApplicationRepository;
    private final AssScoreRepository assScoreRepository;
    private final AssScoreCalculator assScoreCalculator;

    @Transactional
    public CreditLimitApplication saveSubmittedApplication(Long userId, CreditApplicationDraft draft,
                                                           List<UploadedDocument> uploadedDocuments) {
        FarmerProfile profile = farmerProfileRepository.findByUserId(userId)
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
                        userId,
                        draft.getAddress(),
                        draft.getAreaSizeM2(),
                        draft.getCropType(),
                        draft.getHasCropInsurance()
                ));
        FarmerProfile savedProfile = farmerProfileRepository.save(profile);

        CreditLimitApplication application = creditLimitApplicationRepository.save(
                CreditLimitApplication.create(userId)
        );

        uploadedDocuments.stream()
                .map(uploaded -> FarmerDocument.create(
                        uploaded.documentType(),
                        uploaded.fileUrl(),
                        application
                ))
                .forEach(farmerDocumentRepository::save);

        AssScoreResult scoreResult = assScoreCalculator.calculate(savedProfile);
        assScoreRepository.save(AssScore.create(
                application,
                scoreResult.fieldAreaScore(),
                scoreResult.cropScore(),
                scoreResult.insuranceScore(),
                scoreResult.farmingCareerScore()
        ));

        log.info("[CreditSubmit] persisted applicationId={}", application.getPublicId());
        return application;
    }
}
