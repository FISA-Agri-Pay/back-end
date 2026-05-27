package com.kkpp.core.auth.exception;

public class UserAlreadyExistsException extends AuthException {

    public UserAlreadyExistsException() {
        super(AuthErrorCode.USER_ALREADY_EXISTS);
    }
}