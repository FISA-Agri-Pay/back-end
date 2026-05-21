package com.kkpp.core.credit.service;

import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.credit.dto.AssScoreResult;
import org.springframework.stereotype.Service;

@Service
public class AssScoreCalculator {

    public AssScoreResult calculate(FarmerProfile profile) {
        return new AssScoreResult(
                calculateFieldAreaScore(profile.getFieldAreaM2()),
                calculateCropScore(profile),
                calculateInsuranceScore(profile.getHasCropInsurance()),
                calculateFarmingCareerScore(profile)
        );
    }

    private int calculateFieldAreaScore(Double fieldAreaM2) {
        // TODO: ASS 면적 점수 산정 기준 확정 후 구현
        return 0;
    }

    private int calculateCropScore(FarmerProfile profile) {
        // TODO: ASS 작물 점수 산정 기준 확정 후 구현
        return 0;
    }

    private int calculateInsuranceScore(Boolean hasCropInsurance) {
        // TODO: ASS 보험 점수 산정 기준 확정 후 구현
        return 0;
    }

    private int calculateFarmingCareerScore(FarmerProfile profile) {
        // TODO: ASS 영농 경력 점수 산정 기준 확정 후 구현
        return 0;
    }
}
