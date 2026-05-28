package com.kkpp.core.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode {

    USER_ALREADY_EXISTS(409, "USR-001", "이미 존재하는 사용자입니다."),
    USER_NOT_FOUND(404, "USR-002", "사용자를 찾을 수 없습니다."),
    INVALID_PASSWORD(401, "USR-003", "비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(401, "USR-004", "유효하지 않은 리프레시 토큰입니다.");

    private final int status;
    private final String code;
    private final String message;
}