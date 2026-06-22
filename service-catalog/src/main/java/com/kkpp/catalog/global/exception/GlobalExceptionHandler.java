package com.kkpp.catalog.global.exception;

import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.core.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * service-catalog 공통 예외 처리기입니다.
     * 4xx 성격의 예상 가능한 실패는 key-value 로그만 남기고 stack trace는 출력하지 않습니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        log.atWarn()
                .addKeyValue("event", "catalog.business-exception.handled")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", errorCode.getStatus())
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("errorMessage", errorCode.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("카탈로그 비즈니스 예외가 발생했습니다.");

        ErrorResponse error = new ErrorResponse(errorCode.name(), exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.valueOf(errorCode.getStatus()))
                .body(ApiResponse.fail(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.atWarn()
                .addKeyValue("event", "catalog.validation.failed")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                .addKeyValue("invalidFields", message)
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("카탈로그 요청 값 검증에 실패했습니다.");

        ErrorResponse error = new ErrorResponse(ErrorCode.INVALID_REQUEST.name(), message);
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.atWarn()
                .addKeyValue("event", "catalog.data-integrity.failed")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("카탈로그 데이터 제약 조건 오류가 발생했습니다.");

        ErrorResponse error = new ErrorResponse(
                ErrorCode.INVALID_REQUEST.name(),
                "이미 존재하는 값이거나 데이터 제약 조건을 위반했습니다."
        );
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.atError()
                .addKeyValue("event", "catalog.unexpected-exception.handled")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
                .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .addKeyValue("errorMessage", ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .setCause(exception)
                .log("카탈로그 처리 중 예상하지 못한 예외가 발생했습니다.");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR)));
    }
}
