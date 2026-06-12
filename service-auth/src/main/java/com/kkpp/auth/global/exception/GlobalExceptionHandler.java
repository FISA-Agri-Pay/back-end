package com.kkpp.auth.global.exception;

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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * 인증 도메인에 한정되지 않은 공통 예외 처리기입니다.
     * 요청 형식 오류, validation 오류, 예상하지 못한 예외를 key-value 로그로 표준화합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        log.atWarn()
                .addKeyValue("event", "auth.business-exception.handled")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", errorCode.getStatus())
                .addKeyValue("errorCode", errorCode.getCode())
                .addKeyValue("errorMessage", exception.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("비즈니스 예외가 발생했습니다.");

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

        // DTO validation 실패 로그입니다. 어떤 필드가 왜 실패했는지 invalidFields에 남깁니다.
        log.atWarn()
                .addKeyValue("event", "auth.validation.failed")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                .addKeyValue("errorMessage", fieldErrors)
                .addKeyValue("invalidFields", fieldErrors)
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("요청 값 검증에 실패했습니다.");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.INVALID_REQUEST.getCode(), fieldErrors));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.atWarn()
                .addKeyValue("event", "auth.invalid-request.handled")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                .addKeyValue("errorMessage", exception.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("잘못된 요청입니다.");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.INVALID_REQUEST.getCode(), "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        // JSON 형식 오류처럼 요청 본문을 파싱할 수 없을 때 발생합니다. 본문 원문은 남기지 않습니다.
        log.atWarn()
                .addKeyValue("event", "auth.request-body.invalid")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpStatus.BAD_REQUEST.value())
                .addKeyValue("errorCode", ErrorCode.INVALID_REQUEST.getCode())
                .addKeyValue("errorMessage", exception.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("요청 본문을 읽을 수 없습니다.");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.INVALID_REQUEST.getCode(), "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.atError()
                .addKeyValue("event", "auth.unexpected-exception.handled")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
                .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .addKeyValue("errorMessage", exception.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .setCause(exception)
                .log("처리되지 않은 예외가 발생했습니다.");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR)));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
