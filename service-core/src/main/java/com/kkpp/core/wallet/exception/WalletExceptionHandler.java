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

    private static final String CREDIT_SUMMARY_URI = "/api/v1/core/wallet/credit";

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletException(
            WalletException exception,
            HttpServletRequest request
    ) {
        WalletErrorCode errorCode = exception.getErrorCode();

        if (isCreditSummaryRequest(request)) {
            // 상세 로그 대상은 홈 화면 한도 요약 조회 API로 제한합니다.
            log.atWarn()
                    .addKeyValue("event", "wallet.credit.summary.exception.handled")
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("uri", request.getRequestURI())
                    .addKeyValue("status", errorCode.getStatus())
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .log("한도 요약 조회 처리 중 예외가 발생했습니다.");
        } else {
            log.warn("지갑 요청 처리 중 예외가 발생했습니다. method={}, uri={}, code={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    errorCode.getCode(),
                    errorCode.getMessage());
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }

    private boolean isCreditSummaryRequest(HttpServletRequest request) {
        return CREDIT_SUMMARY_URI.equals(request.getRequestURI());
    }
}
