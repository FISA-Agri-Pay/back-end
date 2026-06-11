package com.kkpp.core.global.exception;

import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.core.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static com.kkpp.core.global.logging.MonitoredApiConstants.CREDIT_SUBMIT_URI;
import static com.kkpp.core.global.logging.MonitoredApiConstants.CREDIT_SUMMARY_URI;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        if (isDetailedLoggingTarget(request)) {
            // 모니터링 대상 API에서는 errorCode/status/exceptionType을 key-value로 남겨 Loki 검색을 쉽게 합니다.
            log.atWarn()
                    .addKeyValue("event", "global.business.exception.handled")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", errorCode.getStatus())
                    .addKeyValue("errorCode", errorCode.name())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .log("비즈니스 예외가 발생했습니다.");
        } else {
            log.warn("비즈니스 예외가 발생했습니다. method={}, uri={}, code={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    errorCode.name(),
                    errorCode.getMessage());
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(ErrorResponse.from(errorCode)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        if (isDetailedLoggingTarget(request)) {
            // 요청 본문 원문은 남기지 않고 검증 실패 필드만 남겨 민감정보 노출을 막습니다.
            log.atWarn()
                    .addKeyValue("event", "global.validation.failed")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.name())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .addKeyValue("fields", fieldErrors)
                    .log("요청 값 검증에 실패했습니다.");
        } else {
            log.warn("요청 값 검증에 실패했습니다. method={}, uri={}, code={}, message={}, fields={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ErrorCode.INVALID_REQUEST.name(),
                    ErrorCode.INVALID_REQUEST.getMessage(),
                    fieldErrors);
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorResponse.from(ErrorCode.INVALID_REQUEST)));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestException(
            Exception exception,
            HttpServletRequest request
    ) {
        if (isDetailedLoggingTarget(request)) {
            // 타입 변환 실패나 JSON 파싱 실패는 예외 타입만 남기고 요청 원문은 기록하지 않습니다.
            log.atWarn()
                    .addKeyValue("event", "global.invalid.request.handled")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                    .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.name())
                    .addKeyValue("errorMessage", ErrorCode.INVALID_REQUEST.getMessage())
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .log("잘못된 요청입니다.");
        } else {
            log.warn("잘못된 요청입니다. method={}, uri={}, code={}, message={}, exceptionType={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ErrorCode.INVALID_REQUEST.name(),
                    ErrorCode.INVALID_REQUEST.getMessage(),
                    exception.getClass().getSimpleName());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorResponse.from(ErrorCode.INVALID_REQUEST)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        if (isDetailedLoggingTarget(request)) {
            // 예상하지 못한 오류는 stack trace와 함께 실패 구간을 고정값으로 남깁니다.
            log.atError()
                    .addKeyValue("event", "global.unhandled.exception")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.name())
                    .addKeyValue("errorMessage", ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .addKeyValue("failureState", "UNHANDLED_EXCEPTION")
                    .setCause(exception)
                    .log("처리되지 않은 예외가 발생했습니다.");
        } else {
            log.error("처리되지 않은 예외가 발생했습니다. method={}, uri={}, code={}, message={}, exceptionType={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    ErrorCode.INTERNAL_SERVER_ERROR.name(),
                    ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                    exception.getClass().getSimpleName(),
                    exception);
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR)));
    }

    private boolean isDetailedLoggingTarget(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return CREDIT_SUBMIT_URI.equals(uri) || CREDIT_SUMMARY_URI.equals(uri);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " (" + fieldError.getDefaultMessage() + ")";
    }
}
