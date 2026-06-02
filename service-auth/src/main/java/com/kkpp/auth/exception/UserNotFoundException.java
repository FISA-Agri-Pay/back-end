package com.kkpp.auth.exception;

public class UserNotFoundException extends AuthException {

    public UserNotFoundException() {
        super(AuthErrorCode.USER_NOT_FOUND);
    }
}
