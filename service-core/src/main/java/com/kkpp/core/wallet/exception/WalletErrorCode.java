package com.kkpp.core.wallet.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode {

    WALLET_NOT_FOUND(404, "WLT-001", "지갑을 찾을 수 없습니다."),
    USER_NOT_FOUND(404, "USR-002", "사용자를 찾을 수 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}
