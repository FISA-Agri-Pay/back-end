package com.kkpp.auth.global.logging;

import com.kkpp.auth.exception.AuthException;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import com.kkpp.common.security.auth.AuthUserInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class MonitoredApiLoggingAspect {

    /*
     * @MonitoredApiLogging이 붙은 인증 API의 공통 진입/완료/실패 로그를 남깁니다.
     * 각 API의 비즈니스 로그는 서비스 계층에서 따로 남기고, 여기서는 요청 본문을 절대 기록하지 않습니다.
     */
    @Around("@annotation(monitoredApiLogging)")
    public Object logMonitoredApi(
            ProceedingJoinPoint joinPoint,
            MonitoredApiLogging monitoredApiLogging
    ) throws Throwable {
        long startedAtNanos = System.nanoTime();
        HttpServletRequest request = currentRequest();
        Long userId = findUserId(joinPoint.getArgs());
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";

        // AOP 시작 로그입니다. PIN, 비밀번호, 토큰이 섞일 수 있는 요청 본문은 남기지 않습니다.
        log.atInfo()
                .addKeyValue("event", monitoredApiLogging.event() + ".started")
                .addKeyValue("apiName", monitoredApiLogging.apiName())
                .addKeyValue("method", method)
                .addKeyValue("uri", uri)
                .addKeyValue("userId", userId)
                .log("인증 API 요청 처리를 시작했습니다.");

        try {
            Object result = joinPoint.proceed();
            // AOP 완료 로그입니다. 공통 응답 시간(durationMs)을 모든 인증 API에서 같은 형식으로 남깁니다.
            log.atInfo()
                    .addKeyValue("event", monitoredApiLogging.event() + ".completed")
                    .addKeyValue("apiName", monitoredApiLogging.apiName())
                    .addKeyValue("method", method)
                    .addKeyValue("uri", uri)
                    .addKeyValue("userId", userId)
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .addKeyValue("resultStatus", "SUCCESS")
                    .log("인증 API 요청 처리가 완료되었습니다.");
            return result;
        } catch (Throwable exception) {
            // AOP 실패 로그입니다. 상세 실패 원인은 서비스/예외 핸들러 로그와 errorCode/errorMessage로 연결됩니다.
            LoggingEventBuilder builder = isExpectedException(exception) ? log.atInfo() : log.atWarn();
            builder.addKeyValue("event", monitoredApiLogging.event() + ".failed")
                    .addKeyValue("apiName", monitoredApiLogging.apiName())
                    .addKeyValue("method", method)
                    .addKeyValue("uri", uri)
                    .addKeyValue("userId", userId)
                    .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                    .addKeyValue("resultStatus", "FAILED")
                    .addKeyValue("errorCode", errorCode(exception))
                    .addKeyValue("errorMessage", errorMessage(exception))
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .log("인증 API 요청 처리에 실패했습니다.");
            throw exception;
        }
    }

    // 현재 HTTP 요청 정보를 가져와 method, uri를 공통 로그에 붙입니다.
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    // @AuthUser로 주입된 사용자 ID만 추출합니다. 요청 DTO 내부 값은 민감정보가 있어 읽지 않습니다.
    private Long findUserId(Object[] args) {
        return Arrays.stream(args)
                .filter(AuthUserInfo.class::isInstance)
                .map(AuthUserInfo.class::cast)
                .map(AuthUserInfo::userId)
                .findFirst()
                .orElse(null);
    }

    // 예상 가능한 인증/비즈니스 예외는 경고성 장애가 아니라 정상 실패 흐름으로 낮춰 기록합니다.
    private boolean isExpectedException(Throwable exception) {
        return exception instanceof AuthException || exception instanceof BusinessException;
    }

    private String errorCode(Throwable exception) {
        if (exception instanceof AuthException authException) {
            return authException.getErrorCode().getCode();
        }
        if (exception instanceof BusinessException businessException) {
            ErrorCode errorCode = businessException.getErrorCode();
            return errorCode.getCode();
        }
        return "UNEXPECTED_ERROR";
    }

    private String errorMessage(Throwable exception) {
        if (exception instanceof AuthException authException) {
            return authException.getErrorCode().getMessage();
        }
        return exception.getMessage();
    }
}
