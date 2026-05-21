package com.kkpp.core.credit.domain;

import com.kkpp.core.credit.dto.response.RequiredDocumentResponse;
import com.kkpp.core.credit.exception.CreditErrorCode;
import com.kkpp.core.credit.exception.CreditException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum RequiredDocumentType {

    AGRI_MANAGEMENT_REGISTRATION("농업 경영체 등록 확인서"),
    CROP_DISASTER_INSURANCE("농작물 재해보험 가입 증명서");

    private final String documentName;

    public static List<RequiredDocumentResponse> byInsurance(boolean hasInsurance) {
        return List.of(
                AGRI_MANAGEMENT_REGISTRATION.toResponse(true),
                CROP_DISASTER_INSURANCE.toResponse(hasInsurance)
        );
    }

    private RequiredDocumentResponse toResponse(boolean required) {
        return new RequiredDocumentResponse(name(), documentName, required);
    }

    public static RequiredDocumentType fromDocumentCode(String documentCode) {
        return Arrays.stream(values())
                .filter(documentType -> documentType.name().equals(documentCode))
                .findFirst()
                .orElseThrow(() -> new CreditException(CreditErrorCode.DOCUMENT_REQUIRED_MISSING, documentCode));
    }
}
