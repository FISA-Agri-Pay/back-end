package com.kkpp.auth.exception;

import com.kkpp.common.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    /*
     * AuthException 전용 처리기입니다.
     * 인증 도메인 오류는 errorCode/errorMessage를 반드시 로그와 응답에 함께 남깁니다.
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(
            AuthException exception,
            HttpServletRequest request
    ) {
        AuthErrorCode errorCode = exception.getErrorCode();
        HttpStatus status = HttpStatus.valueOf(errorCode.getStatus());
        LoggingEventBuilder builder = status.is5xxServerError() ? log.atError().setCause(exception) : log.atWarn();

        builder.addKeyValue("event", "auth.exception.handled")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", status.value())
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("errorMessage", errorCode.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log(status.is5xxServerError()
                        ? "인증 요청 처리 중 서버 예외가 발생했습니다."
                        : "인증 요청 처리 중 예외가 발생했습니다.");

        return ResponseEntity
                .status(status)
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
