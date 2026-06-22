package com.kkpp.core.credit.domain;

import static com.kkpp.core.testsupport.TestEntityFactory.application;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.kkpp.core.credit.dto.response.RequiredDocumentResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreditDomainTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID APPLICATION_PUBLIC_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void cropTypeFromTrimsValidCodeAndRejectsInvalidCode() {
        assertThat(CropType.from(" RICE ")).isEqualTo(CropType.RICE);

        assertThatThrownBy(() -> CropType.from("APPLE"))
                .hasMessage("지원하지 않는 작물 코드입니다.");
    }

    @Test
    void requiredDocumentTypeReturnsRequiredInsuranceDocumentsAndRejectsUnknownCode() {
        List<RequiredDocumentResponse> responses = RequiredDocumentType.byInsurance(false);

        assertThat(responses).hasSize(2);
        assertThat(responses)
                .extracting(RequiredDocumentResponse::documentCode, RequiredDocumentResponse::isRequired)
                .containsExactlyInAnyOrder(
                        tuple(RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION.name(), true),
                        tuple(RequiredDocumentType.CROP_DISASTER_INSURANCE.name(), false)
                );
        assertThat(RequiredDocumentType.fromDocumentCode("AGRI_MANAGEMENT_REGISTRATION"))
                .isEqualTo(RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION);

        assertThatThrownBy(() -> RequiredDocumentType.fromDocumentCode("UNKNOWN"))
                .hasMessage("필수 서류가 누락되었습니다.");
    }

    @Test
    void farmerDocumentCreateValidatesRequiredValues() {
        FarmerDocument document = FarmerDocument.create(
                USER_PUBLIC_ID,
                APPLICATION_PUBLIC_ID,
                RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION,
                "https://files/agri.pdf"
        );

        assertThat(document.getPublicId()).isNotNull();
        assertThat(document.getUserPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(document.getDocumentType()).isEqualTo(RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION);

        assertThatNullPointerException()
                .isThrownBy(() -> FarmerDocument.create(null, APPLICATION_PUBLIC_ID, RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION, "url"));
        assertThatThrownBy(() -> FarmerDocument.create(USER_PUBLIC_ID, APPLICATION_PUBLIC_ID, RequiredDocumentType.AGRI_MANAGEMENT_REGISTRATION, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assScoreCreateCopiesApplicationAndScoreResultFields() {
        CreditLimitApplication application = application(USER_PUBLIC_ID, ApplicationStatus.PENDING);
        LocalDate priceSnapshotDate = LocalDate.of(2026, 6, 9);
        LocalDateTime calculatedAt = LocalDateTime.of(2026, 6, 9, 13, 0);

        AssScore score = AssScore.create(
                application,
                new BigDecimal("15000000"),
                priceSnapshotDate,
                36,
                25,
                7,
                68,
                calculatedAt
        );

        assertThat(score.getPublicId()).isNotNull();
        assertThat(score.getUserPublicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(score.getApplicationPublicId()).isEqualTo(application.getPublicId());
        assertThat(score.getEstimatedIncome()).isEqualByComparingTo("15000000");
        assertThat(score.getPriceSnapshotDate()).isEqualTo(priceSnapshotDate);
        assertThat(score.getTotalScore()).isEqualTo(68);
        assertThat(score.getCalculatedAt()).isEqualTo(calculatedAt);
    }

    @Test
    void creditLimitApplicationCreateValidatesRequiredValues() {
        CreditLimitApplication application = CreditLimitApplication.create(USER_PUBLIC_ID, new BigDecimal("1000000"));

        assertThat(application.getPublicId()).isNotNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(application.getIsReapplication()).isFalse();

        assertThatNullPointerException()
                .isThrownBy(() -> CreditLimitApplication.create(null, new BigDecimal("1000000")));
        assertThatNullPointerException()
                .isThrownBy(() -> CreditLimitApplication.create(USER_PUBLIC_ID, null));
    }
}
