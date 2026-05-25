package com.kkpp.core.credit.exception;

import lombok.Getter;

@Getter
public class CreditException extends RuntimeException {

    private final CreditErrorCode errorCode;
    private final Object inputValue;

    public CreditException(CreditErrorCode errorCode) {
        this(errorCode, null);
    }

    public CreditException(CreditErrorCode errorCode, Object inputValue) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.inputValue = inputValue;
    }
}
