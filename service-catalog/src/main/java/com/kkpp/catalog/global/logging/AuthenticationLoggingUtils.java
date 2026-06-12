package com.kkpp.catalog.global.logging;

import com.kkpp.common.core.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthenticationLoggingUtils {

    /*
     * local/dev/prod 보안 설정에서 공통으로 사용하는 인증 실패 로그입니다.
     * JWT 누락, 만료, 위변조처럼 요청이 컨트롤러까지 도달하지 못한 상황을 같은 key-value 구조로 남깁니다.
     */
    public static void logAuthenticationFailure(Logger log, HttpServletRequest request, Exception exception) {
        log.atWarn()
                .addKeyValue("event", "catalog.authentication.failed")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("status", HttpServletResponse.SC_UNAUTHORIZED)
                .addKeyValue("errorCode", ErrorCode.UNAUTHORIZED.getCode())
                .addKeyValue("errorMessage", ErrorCode.UNAUTHORIZED.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("카탈로그 서비스 인증에 실패했습니다.");
    }
}
