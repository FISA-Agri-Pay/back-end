package com.kkpp.admin.global.exception;

import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.common.core.response.ErrorResponse;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
// 관리자 API에서 발생한 예외를 공통 응답 포맷으로 변환하는 전역 핸들러임
public class GlobalExceptionHandler {

    // 서비스 계층의 비즈니스 예외를 ErrorCode 기준 HTTP 상태로 응답함
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse errorResponse = new ErrorResponse(errorCode.name(), exception.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorResponse));
    }

    // RequestBody 검증 실패를 400 응답과 필드별 메시지로 변환함
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(FieldError::getField))
                .entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue().getFirst().getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(new ErrorResponse(ErrorCode.INVALID_REQUEST.name(), message)));
    }

    // enum, UUID 등 요청 파라미터 타입 변환 실패를 잘못된 요청으로 응답함
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorResponse.from(ErrorCode.INVALID_REQUEST)));
    }
  
    // multipart 업로드 크기 제한을 초과한 경우 상품 이미지 업로드에서 이해하기 쉬운 400 응답으로 변환함
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException() {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(new ErrorResponse(
                        ErrorCode.INVALID_REQUEST.name(),
                        "상품 이미지는 10MB 이하만 업로드할 수 있습니다."
                )));
    }
}
