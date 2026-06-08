package com.kkpp.admin.global.exception;

import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.core.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn(
                "관리자 API 비즈니스 예외 발생: 메서드={}, 경로={}, 코드={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                errorCode.getCode(),
                exception.getMessage()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(new ErrorResponse(errorCode.getCode(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        log.warn(
                "관리자 API 요청 본문 검증 실패: 메서드={}, 경로={}, 필드오류={}",
                request.getMethod(),
                request.getRequestURI(),
                message
        );

        return invalidRequest(message);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.warn(
                "관리자 API 요청 값 검증 실패: 메서드={}, 경로={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );

        return invalidRequest("요청 값이 올바르지 않습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "관리자 API 요청 파라미터 변환 실패: 메서드={}, 경로={}, 파라미터={}, 값={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getName(),
                exception.getValue()
        );

        return invalidRequest(exception.getName() + " 값 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "관리자 API 요청 본문을 읽을 수 없습니다: 메서드={}, 경로={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );

        return invalidRequest("요청 본문 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "관리자 API 업로드 파일 크기 초과: 메서드={}, 경로={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );

        return invalidRequest("상품 이미지는 10MB 이하만 업로드할 수 있습니다.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "관리자 API 접근 권한이 없습니다: 메서드={}, 경로={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(new ErrorResponse(ErrorCode.FORBIDDEN.getCode(), "권한이 없습니다.")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "관리자 API 데이터 제약 조건 위반: 메서드={}, 경로={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );

        return invalidRequest("데이터 제약 조건을 만족하지 않습니다.");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessException(
            DataAccessException exception,
            HttpServletRequest request
    ) {
        log.error(
                "관리자 API 데이터베이스 처리 중 예외 발생: 메서드={}, 경로={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage(),
                exception
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(new ErrorResponse(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "데이터베이스 처리 중 오류가 발생했습니다."
                )));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "관리자 API 처리 중 예기치 못한 예외 발생: 메서드={}, 경로={}, 메시지={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage(),
                exception
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(new ErrorResponse(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        "서버 오류가 발생했습니다."
                )));
    }

    private ResponseEntity<ApiResponse<Void>> invalidRequest(String message) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(new ErrorResponse(ErrorCode.INVALID_REQUEST.getCode(), message)));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
