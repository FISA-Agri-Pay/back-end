package com.kkpp.core.wallet.exception;

import com.kkpp.common.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(1)
@RestControllerAdvice
public class WalletExceptionHandler {

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletException(
            WalletException exception,
            HttpServletRequest request
    ) {
        WalletErrorCode errorCode = exception.getErrorCode();
        log.warn("지갑 조회 처리 중 예외가 발생했습니다. method={}, uri={}, code={}",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.getCode());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
