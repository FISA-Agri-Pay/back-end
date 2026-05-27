package com.kkpp.core.auth.exception;

public class InvalidPasswordException extends AuthException {

    public InvalidPasswordException() {
        super(AuthErrorCode.INVALID_PASSWORD);
    }
}