package com.kkpp.core.wallet.exception;

import lombok.Getter;

@Getter
public class WalletException extends RuntimeException {

    private final WalletErrorCode errorCode;

    public WalletException(WalletErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
