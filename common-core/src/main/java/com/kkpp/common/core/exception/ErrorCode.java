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
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
