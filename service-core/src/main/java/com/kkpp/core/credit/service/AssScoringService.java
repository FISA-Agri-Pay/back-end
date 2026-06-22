package com.kkpp.core.credit.service;

import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.credit.dto.AssScoreResult;
import com.kkpp.core.global.logging.LogMaskingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Service
public class AssScoringService {

    private static final BigDecimal TEN_ARE_M2 = new BigDecimal("1000");
    private static final Map<CropType, CropPolicy> CROP_POLICIES = Map.of(
            CropType.RICE, new CropPolicy(524, 2450),
            CropType.GARLIC, new CropPolicy(1223, 2045),
            CropType.ONION, new CropPolicy(6308, 300),
            CropType.PEPPER, new CropPolicy(225, 15000),
            CropType.BEAN, new CropPolicy(209, 3000)
    );

    public AssScoreResult calculate(FarmerProfile profile, CropType cropType) {
        // ASS 산정은 한도 접수의 핵심 판단 구간이라 입력 식별자와 산정 결과를 별도 이벤트로 남깁니다.
        log.atInfo()
                .addKeyValue("event", "credit.ass.scoring.started")
                .addKeyValue("profilePublicId", LogMaskingUtils.maskIdentifier(profile.getPublicId()))
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(profile.getUserPublicId()))
                .addKeyValue("cropType", cropType)
                .addKeyValue("hasCropInsurance", profile.getHasCropInsurance())
                .log("ASS 점수 산정을 시작했습니다.");

        try {
            BigDecimal estimatedIncome = calculateEstimatedIncome(profile.getFieldAreaM2(), cropType);
            int incomeScore = calculateIncomeScore(estimatedIncome);
            int insuranceScore = calculateInsuranceScore(profile.getHasCropInsurance());
            int farmingCareerScore = calculateFarmingCareerScore(profile);
            int totalScore = incomeScore + insuranceScore + farmingCareerScore;

            AssScoreResult result = new AssScoreResult(
                    estimatedIncome,
                    LocalDate.now(),
                    incomeScore,
                    insuranceScore,
                    farmingCareerScore,
                    totalScore,
                    LocalDateTime.now()
            );

            // 점수 구성요소를 남겨 이후 심사 결과 문의나 산정 오류를 역추적할 수 있게 합니다.
            log.atInfo()
                    .addKeyValue("event", "credit.ass.scoring.completed")
                    .addKeyValue("profilePublicId", LogMaskingUtils.maskIdentifier(profile.getPublicId()))
                    .addKeyValue("cropType", cropType)
                    .addKeyValue("incomeScore", incomeScore)
                    .addKeyValue("insuranceScore", insuranceScore)
                    .addKeyValue("farmingCareerScore", farmingCareerScore)
                    .addKeyValue("totalScore", totalScore)
                    .log("ASS 점수 산정을 완료했습니다.");
            return result;
        } catch (RuntimeException exception) {
            // 산정 중 예외가 나면 입력 cropType과 실패 구간을 함께 남겨 재현 범위를 줄입니다.
            log.atError()
                    .addKeyValue("event", "credit.ass.scoring.failed")
                    .addKeyValue("profilePublicId", LogMaskingUtils.maskIdentifier(profile.getPublicId()))
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(profile.getUserPublicId()))
                    .addKeyValue("cropType", cropType)
                    .addKeyValue("failureState", "CALCULATING_ASS_SCORE")
                    .setCause(exception)
                    .log("ASS 점수 산정 중 오류가 발생했습니다.");
            throw exception;
        }
    }

    private BigDecimal calculateEstimatedIncome(BigDecimal fieldAreaM2, CropType cropType) {
        if (fieldAreaM2 == null || fieldAreaM2.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        CropPolicy cropPolicy = CROP_POLICIES.get(cropType);
        if (cropPolicy == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        BigDecimal areaTenAre = fieldAreaM2.divide(TEN_ARE_M2, 6, RoundingMode.HALF_UP);
        return areaTenAre.multiply(cropPolicy.incomePerTenAre()).setScale(2, RoundingMode.HALF_UP);
    }

    private int calculateIncomeScore(BigDecimal estimatedIncome) {
        if (estimatedIncome.compareTo(new BigDecimal("50000000")) >= 0) {
            return 60;
        }
        if (estimatedIncome.compareTo(new BigDecimal("30000000")) >= 0) {
            return 48;
        }
        if (estimatedIncome.compareTo(new BigDecimal("15000000")) >= 0) {
            return 36;
        }
        if (estimatedIncome.compareTo(new BigDecimal("8000000")) >= 0) {
            return 24;
        }
        return 12;
    }

    private int calculateInsuranceScore(Boolean hasCropInsurance) {
        return Boolean.TRUE.equals(hasCropInsurance) ? 25 : 0;
    }

    private int calculateFarmingCareerScore(FarmerProfile profile) {
        if (profile.getFarmingSince() == null) {
            // TODO: 농업 경영체 등록일 연동 전까지 MVP 기본값(1년 이상 3년 미만 구간)을 사용한다.
            return 7;
        }

        long farmingYears = profile.getFarmingSince();
        if (farmingYears >= 5) {
            return 15;
        }
        if (farmingYears >= 3) {
            return 11;
        }
        if (farmingYears >= 1) {
            return 7;
        }
        return 3;
    }

    private record CropPolicy(int yieldKgPerTenAre, int pricePerKg) {

        private BigDecimal incomePerTenAre() {
            return BigDecimal.valueOf(yieldKgPerTenAre)
                    .multiply(BigDecimal.valueOf(pricePerKg));
        }
    }
}
