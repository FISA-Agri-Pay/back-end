package com.kkpp.core.global.logging;

import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.security.auth.AuthUserInfo;
import com.kkpp.core.credit.exception.CreditException;
import com.kkpp.core.wallet.exception.WalletException;
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

    @Around("@annotation(monitoredApiLogging)")
    public Object logMonitoredApi(
            ProceedingJoinPoint joinPoint,
            MonitoredApiLogging monitoredApiLogging
    ) throws Throwable {
        long startedAtNanos = System.nanoTime();
        HttpServletRequest request = currentRequest();
        Long userId = findUserId(joinPoint.getArgs());
        String sessionId = findSessionId(joinPoint.getArgs());

        // 반복되는 API 요청 시작 로그와 처리 시간 측정은 AOP에서 담당해 비즈니스 로직과 분리합니다.
        log.atInfo()
                .addKeyValue("event", monitoredApiLogging.event() + ".started")
                .addKeyValue("apiName", monitoredApiLogging.apiName())
                .addKeyValue("method", request == null ? null : request.getMethod())
                .addKeyValue("uri", request == null ? null : request.getRequestURI())
                .addKeyValue("userId", userId)
                .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                .log("모니터링 대상 API 요청을 시작했습니다.");

        try {
            Object result = joinPoint.proceed();
            log.atInfo()
                    .addKeyValue("event", monitoredApiLogging.event() + ".completed")
                    .addKeyValue("apiName", monitoredApiLogging.apiName())
                    .addKeyValue("method", request == null ? null : request.getMethod())
                    .addKeyValue("uri", request == null ? null : request.getRequestURI())
                    .addKeyValue("userId", userId)
                    .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                    .addKeyValue("durationMs", elapsedMillis(startedAtNanos))
                    .log("모니터링 대상 API 요청을 완료했습니다.");
            return result;
        } catch (Throwable exception) {
            log.atWarn()
                    .addKeyValue("event", monitoredApiLogging.event() + ".failed")
                    .addKeyValue("apiName", monitoredApiLogging.apiName())
                    .addKeyValue("method", request == null ? null : request.getMethod())
                    .addKeyValue("uri", request == null ? null : request.getRequestURI())
                    .addKeyValue("userId", userId)
                    .addKeyValue("sessionId", LogMaskingUtils.maskIdentifier(sessionId))
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .addKeyValue("errorCode", errorCode(exception))
                    .addKeyValue("errorMessage", errorMessage(exception))
                    .addKeyValue("failureState", "API_REQUEST_FAILED")
                    .addKeyValue("durationMs", elapsedMillis(startedAtNanos))
                    .setCause(exception)
                    .log("모니터링 대상 API 요청 처리에 실패했습니다.");
            throw exception;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Long findUserId(Object[] args) {
        return Arrays.stream(args)
                .filter(AuthUserInfo.class::isInstance)
                .map(AuthUserInfo.class::cast)
                .map(AuthUserInfo::userId)
                .findFirst()
                .orElse(null);
    }

    private String findSessionId(Object[] args) {
        return Arrays.stream(args)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    private String errorCode(Throwable exception) {
        if (exception instanceof CreditException creditException) {
            return creditException.getErrorCode().getCode();
        }
        if (exception instanceof WalletException walletException) {
            return walletException.getErrorCode().getCode();
        }
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }
        return null;
    }

    private String errorMessage(Throwable exception) {
        if (exception instanceof CreditException creditException) {
            return creditException.getErrorCode().getMessage();
        }
        if (exception instanceof WalletException walletException) {
            return walletException.getErrorCode().getMessage();
        }
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getMessage();
        }
        return exception.getMessage();
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
