package com.kkpp.common.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(400, "INVALID_REQUEST", "잘못된 요청입니다."),
    UNAUTHORIZED(401, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다."),
    RESOURCE_NOT_FOUND(404, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."),

    // credit
    SESSION_NOT_FOUND(404, "SESSION_NOT_FOUND", "유효하지 않은 세션입니다."),
    APPLICATION_DUPLICATE(409, "APPLICATION_DUPLICATE", "이미 접수된 심사 내역이 존재합니다."),
    LAND_INVALID_ADDRESS(400, "LND-001", "유효하지 않은 주소 형식입니다."),
    LAND_INVALID_AREA_SIZE(400, "LND-002", "경작 면적은 0보다 커야 합니다."),
    LAND_UNSUPPORTED_REGION(400, "LND-003", "지원하지 않는 지역입니다.");

    private final int status;
    private final String code;
    private final String message;
}
