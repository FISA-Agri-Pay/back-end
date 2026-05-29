package com.kkpp.core.credit.exception;

import com.kkpp.common.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class CreditExceptionHandler {

    @ExceptionHandler(CreditException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditException(
            CreditException exception,
            HttpServletRequest request
    ) {
        CreditErrorCode errorCode = exception.getErrorCode();
        log.warn("한도 심사 요청 처리 중 예외가 발생했습니다. method={}, uri={}, code={}",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.getCode());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
