package com.kkpp.core.credit.service;

import static com.kkpp.core.testsupport.TestEntityFactory.farmerProfile;
import static com.kkpp.core.testsupport.TestEntityFactory.set;
import static org.assertj.core.api.Assertions.assertThat;

import com.kkpp.core.credit.domain.CropType;
import com.kkpp.core.credit.domain.FarmerProfile;
import com.kkpp.core.credit.dto.AssScoreResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AssScoringServiceTest {

    private final AssScoringService assScoringService = new AssScoringService();

    @Test
    void calculateReturnsHighIncomeInsuranceAndCareerScores() {
        FarmerProfile profile = farmerProfile(new BigDecimal("20000"), CropType.PEPPER, true);
        set(profile, "farmingSince", 5);

        AssScoreResult result = assScoringService.calculate(profile, CropType.PEPPER);

        assertThat(result.estimatedIncome()).isEqualByComparingTo("67500000.00");
        assertThat(result.incomeScore()).isEqualTo(60);
        assertThat(result.insuranceScore()).isEqualTo(25);
        assertThat(result.farmingCareerScore()).isEqualTo(15);
        assertThat(result.totalScore()).isEqualTo(100);
    }

    @Test
    void calculateReturnsZeroIncomeWhenAreaIsMissingOrCropPolicyIsUnknown() {
        FarmerProfile profile = farmerProfile(null, CropType.RICE, false);
        set(profile, "farmingSince", 0);

        AssScoreResult result = assScoringService.calculate(profile, null);

        assertThat(result.estimatedIncome()).isEqualByComparingTo("0.00");
        assertThat(result.incomeScore()).isEqualTo(12);
        assertThat(result.insuranceScore()).isZero();
        assertThat(result.farmingCareerScore()).isEqualTo(3);
        assertThat(result.totalScore()).isEqualTo(15);
    }

    @Test
    void calculateAppliesMiddleIncomeThresholdsAndDefaultCareerScore() {
        FarmerProfile profile = farmerProfile(new BigDecimal("10000"), CropType.ONION, false);
        set(profile, "farmingSince", null);

        AssScoreResult result = assScoringService.calculate(profile, CropType.ONION);

        assertThat(result.estimatedIncome()).isEqualByComparingTo("18924000.00");
        assertThat(result.incomeScore()).isEqualTo(36);
        assertThat(result.farmingCareerScore()).isEqualTo(7);
        assertThat(result.totalScore()).isEqualTo(43);
    }

    @Test
    void calculateUsesCareerThresholds() {
        FarmerProfile threeYearProfile = farmerProfile(new BigDecimal("7000"), CropType.RICE, true);
        FarmerProfile oneYearProfile = farmerProfile(new BigDecimal("7000"), CropType.RICE, true);
        set(threeYearProfile, "farmingSince", 3);
        set(oneYearProfile, "farmingSince", 1);

        AssScoreResult threeYearResult = assScoringService.calculate(threeYearProfile, CropType.RICE);
        AssScoreResult oneYearResult = assScoringService.calculate(oneYearProfile, CropType.RICE);

        assertThat(threeYearResult.farmingCareerScore()).isEqualTo(11);
        assertThat(oneYearResult.farmingCareerScore()).isEqualTo(7);
    }

    @Test
    void calculateAppliesRemainingIncomeThresholds() {
        FarmerProfile upperMiddle = farmerProfile(new BigDecimal("25000"), CropType.RICE, false);
        FarmerProfile lowerMiddle = farmerProfile(new BigDecimal("7000"), CropType.ONION, false);

        AssScoreResult upperMiddleResult = assScoringService.calculate(upperMiddle, CropType.RICE);
        AssScoreResult lowerMiddleResult = assScoringService.calculate(lowerMiddle, CropType.ONION);

        assertThat(upperMiddleResult.estimatedIncome()).isEqualByComparingTo("32095000.00");
        assertThat(upperMiddleResult.incomeScore()).isEqualTo(48);
        assertThat(lowerMiddleResult.estimatedIncome()).isEqualByComparingTo("13246800.00");
        assertThat(lowerMiddleResult.incomeScore()).isEqualTo(24);
    }
}
