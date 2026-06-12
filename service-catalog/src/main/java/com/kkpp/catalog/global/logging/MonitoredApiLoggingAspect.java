package com.kkpp.catalog.global.logging;

import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.security.auth.AuthUserInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class MonitoredApiLoggingAspect {

    /*
     * @MonitoredApiLogging이 붙은 catalog API의 공통 진입/완료/실패 로그를 남깁니다.
     * 배송지/전화번호/멱등키 같은 요청 본문 값은 서비스 계층에서 필요한 범위만 마스킹해 남깁니다.
     */
    @Around("@annotation(monitoredApiLogging)")
    public Object logMonitoredApi(
            ProceedingJoinPoint joinPoint,
            MonitoredApiLogging monitoredApiLogging
    ) throws Throwable {
        long startedAtNanos = System.nanoTime();
        HttpServletRequest request = currentRequest();
        AuthUserInfo authUser = findAuthUser(joinPoint.getArgs());
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";

        log.atInfo()
                .addKeyValue("event", monitoredApiLogging.event() + ".started")
                .addKeyValue("apiName", monitoredApiLogging.apiName())
                .addKeyValue("method", method)
                .addKeyValue("uri", uri)
                .addKeyValue("userId", authUser == null ? null : authUser.userId())
                .addKeyValue("userPublicId", authUser == null ? null : LogMaskingUtils.maskIdentifier(authUser.publicId()))
                .log("카탈로그 API 요청 처리를 시작했습니다.");

        try {
            Object result = joinPoint.proceed();
            log.atInfo()
                    .addKeyValue("event", monitoredApiLogging.event() + ".completed")
                    .addKeyValue("apiName", monitoredApiLogging.apiName())
                    .addKeyValue("method", method)
                    .addKeyValue("uri", uri)
                    .addKeyValue("userId", authUser == null ? null : authUser.userId())
                    .addKeyValue("userPublicId", authUser == null ? null : LogMaskingUtils.maskIdentifier(authUser.publicId()))
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .addKeyValue("resultStatus", "SUCCESS")
                    .log("카탈로그 API 요청 처리가 완료되었습니다.");
            return result;
        } catch (Throwable exception) {
            log.atWarn()
                    .addKeyValue("event", monitoredApiLogging.event() + ".failed")
                    .addKeyValue("apiName", monitoredApiLogging.apiName())
                    .addKeyValue("method", method)
                    .addKeyValue("uri", uri)
                    .addKeyValue("userId", authUser == null ? null : authUser.userId())
                    .addKeyValue("userPublicId", authUser == null ? null : LogMaskingUtils.maskIdentifier(authUser.publicId()))
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .addKeyValue("resultStatus", "FAILED")
                    .addKeyValue("errorCode", errorCode(exception))
                    .addKeyValue("errorMessage", errorMessage(exception))
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .log("카탈로그 API 요청 처리에 실패했습니다.");
            throw exception;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private AuthUserInfo findAuthUser(Object[] args) {
        return Arrays.stream(args)
                .filter(AuthUserInfo.class::isInstance)
                .map(AuthUserInfo.class::cast)
                .findFirst()
                .orElse(null);
    }

    private String errorCode(Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getCode();
        }
        return "UNEXPECTED_ERROR";
    }

    private String errorMessage(Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            ErrorCode errorCode = businessException.getErrorCode();
            return errorCode.getMessage();
        }
        return "예상하지 못한 오류가 발생했습니다.";
    }
}
