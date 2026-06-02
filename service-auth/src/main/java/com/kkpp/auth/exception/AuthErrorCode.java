package com.kkpp.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode {

    USER_ALREADY_EXISTS(409, "USR-001", "이미 가입된 사용자입니다."),
    LOGIN_FAILED(401, "USR-002", "휴대폰 번호 또는 비밀번호가 올바르지 않습니다."),
    USER_NOT_FOUND(404, "USR-003", "사용자를 찾을 수 없습니다."),
    INVALID_REFRESH_TOKEN(401, "USR-004", "유효하지 않은 refresh token입니다.");

    private final int status;
    private final String code;
    private final String message;
}
