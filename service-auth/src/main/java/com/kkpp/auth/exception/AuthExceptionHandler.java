package com.kkpp.auth.exception;

import com.kkpp.common.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(
            AuthException exception,
            HttpServletRequest request
    ) {
        AuthErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());
        if (status.is5xxServerError()) {
            log.error(
                    "인증 요청 처리 중 서버 예외가 발생했습니다. method={}, uri={}, code={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    errorCode.getCode(),
                    errorCode.getMessage(),
                    exception
            );
        } else {
            log.warn(
                    "인증 요청 처리 중 예외가 발생했습니다. method={}, uri={}, code={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    errorCode.getCode(),
                    errorCode.getMessage()
            );
        }

        return ResponseEntity
                .status(status)
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
