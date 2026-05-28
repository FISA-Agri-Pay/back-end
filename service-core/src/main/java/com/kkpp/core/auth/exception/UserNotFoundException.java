package com.kkpp.core.auth.exception;

public class UserNotFoundException extends AuthException {

    public UserNotFoundException() {
        super(AuthErrorCode.USER_NOT_FOUND);
    }
}