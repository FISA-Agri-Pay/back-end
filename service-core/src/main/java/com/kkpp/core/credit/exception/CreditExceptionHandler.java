package com.kkpp.core.credit.exception;

import com.kkpp.common.core.response.ApiResponse;
import com.kkpp.core.global.logging.LogMaskingUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.kkpp.core.global.logging.MonitoredApiConstants.CREDIT_SUBMIT_URI;

@Slf4j
@RestControllerAdvice
public class CreditExceptionHandler {

    @ExceptionHandler(CreditException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditException(
            CreditException exception,
            HttpServletRequest request
    ) {
        CreditErrorCode errorCode = exception.getErrorCode();

        if (isCreditSubmitRequest(request)) {
            // 상세 로그 대상은 최종 한도 심사 접수 API로 제한합니다.
            log.atWarn()
                    .addKeyValue("event", "credit.submit.exception.handled")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", errorCode.getStatus())
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .addKeyValue("inputContext", LogMaskingUtils.describeSafe(exception.getInputValue()))
                    .log("한도 심사 접수 요청이 비즈니스 규칙 검증에 실패했습니다.");
        } else {
            log.warn("한도 심사 요청 처리 중 예외가 발생했습니다. method={}, uri={}, code={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    errorCode.getCode(),
                    errorCode.getMessage());
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }

    private boolean isCreditSubmitRequest(HttpServletRequest request) {
        return CREDIT_SUBMIT_URI.equals(request.getRequestURI());
    }
}
