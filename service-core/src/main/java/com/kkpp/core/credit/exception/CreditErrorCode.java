package com.kkpp.core.credit.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CreditErrorCode {

    SESSION_ID_REQUIRED(400, "SES-001", "세션 ID가 누락되었습니다."),
    SESSION_NOT_FOUND(404, "SES-002", "유효하지 않은 세션입니다."),
    SESSION_EXPIRED(410, "SES-003", "만료된 심사 세션입니다."),

    LAND_INVALID_ADDRESS(400, "LND-001", "유효하지 않은 주소 형식입니다."),
    LAND_INVALID_AREA_SIZE(400, "LND-002", "경작 면적은 0보다 커야 합니다."),
    LAND_UNSUPPORTED_REGION(400, "LND-003", "지원하지 않는 지역입니다."),

    CROP_UNSUPPORTED_TYPE(400, "CRP-001", "지원하지 않는 작물 코드입니다."),

    DOCUMENT_REQUIRED_MISSING(400, "DOC-001", "필수 서류가 누락되었습니다."),
    DOCUMENT_SIZE_EXCEEDED(413, "DOC-002", "파일 크기가 제한을 초과했습니다."),
    DOCUMENT_UNSUPPORTED_TYPE(415, "DOC-003", "지원하지 않는 파일 형식입니다."),

    APPLICATION_DUPLICATE(409, "APP-001", "이미 접수된 심사 내역이 존재합니다.");

    private final int status;
    private final String code;
    private final String message;
}
