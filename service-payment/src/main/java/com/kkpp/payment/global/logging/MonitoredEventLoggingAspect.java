package com.kkpp.payment.global.logging;

import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
import com.kkpp.payment.global.tracing.TracingSupport;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MonitoredEventLoggingAspect {

    private final TracingSupport tracingSupport;

    /*
     * @MonitoredEventLogging이 붙은 이벤트 처리 메서드의 시작/완료/실패 로그와 custom span 생성을 담당합니다.
     * SQS/Kafka consumer와 DB 반영 서비스에서 반복되는 시간 측정, 결과 상태, 오류 정보를 비즈니스 코드 밖으로 분리합니다.
     */
    @Around("@annotation(monitoredEventLogging)")
    public Object logMonitoredEvent(
            ProceedingJoinPoint joinPoint,
            MonitoredEventLogging monitoredEventLogging
    ) throws Throwable {
        long startedAtNanos = System.nanoTime();
        String spanName = monitoredEventLogging.spanName();
        Span span = spanName.isBlank() ? null : tracingSupport.startSpan(spanName);

        try (Scope ignored = span == null ? null : span.makeCurrent()) {
            logStarted(joinPoint, monitoredEventLogging);
            Object result = joinPoint.proceed();
            logCompleted(joinPoint, monitoredEventLogging, startedAtNanos);
            return result;
        } catch (Throwable exception) {
            tracingSupport.recordException(span, exception);
            logFailed(joinPoint, monitoredEventLogging, startedAtNanos, exception);
            throw exception;
        } finally {
            if (span != null) {
                span.end();
            }
        }
    }

    private void logStarted(ProceedingJoinPoint joinPoint, MonitoredEventLogging annotation) {
        log.atInfo()
                .addKeyValue("event", annotation.event() + ".started")
                .addKeyValue("operationName", annotation.operationName())
                .addKeyValue("messageContext", messageContext(joinPoint.getArgs()))
                .log("결제 이벤트 처리 작업을 시작했습니다.");
    }

    private void logCompleted(
            ProceedingJoinPoint joinPoint,
            MonitoredEventLogging annotation,
            long startedAtNanos
    ) {
        log.atInfo()
                .addKeyValue("event", annotation.event() + ".completed")
                .addKeyValue("operationName", annotation.operationName())
                .addKeyValue("messageContext", messageContext(joinPoint.getArgs()))
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .addKeyValue("resultStatus", "SUCCESS")
                .log("결제 이벤트 처리 작업이 완료되었습니다.");
    }

    private void logFailed(
            ProceedingJoinPoint joinPoint,
            MonitoredEventLogging annotation,
            long startedAtNanos,
            Throwable exception
    ) {
        log.atError()
                .addKeyValue("event", annotation.event() + ".failed")
                .addKeyValue("operationName", annotation.operationName())
                .addKeyValue("messageContext", messageContext(joinPoint.getArgs()))
                .addKeyValue("durationMs", LoggingTimeUtils.elapsedMillis(startedAtNanos))
                .addKeyValue("resultStatus", "FAILED")
                .addKeyValue("failureState", failureState(exception))
                .addKeyValue("errorMessage", exception.getMessage())
                .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                .log("결제 이벤트 처리 작업이 실패했습니다.");
    }

    private String failureState(Throwable exception) {
        if (exception instanceof PaymentProcessingException) {
            return "PAYMENT_PROCESSING_FAILED";
        }
        return "UNEXPECTED_ERROR";
    }

    private String messageContext(Object[] args) {
        return Arrays.stream(args)
                .map(this::describeArgument)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String describeArgument(Object arg) {
        if (arg instanceof CreditPaymentRequestedMessage message) {
            return "eventId=" + LogMaskingUtils.maskIdentifier(message.eventId())
                    + ", paymentRequestPublicId=" + LogMaskingUtils.maskUuid(message.paymentRequestPublicId())
                    + ", orderPublicId=" + LogMaskingUtils.maskUuid(message.orderPublicId())
                    + ", userPublicId=" + LogMaskingUtils.maskUuid(message.userPublicId())
                    + ", totalAmount=" + message.totalAmount()
                    + ", items=" + LogMaskingUtils.summarizeCollection(message.items());
        }
        if (arg instanceof Message message) {
            return "sqsMessageId=" + LogMaskingUtils.maskIdentifier(message.messageId())
                    + ", traceContextPresent=" + message.messageAttributes().containsKey("traceparent");
        }
        if (arg instanceof ConsumerRecord<?, ?> record) {
            return "topic=" + record.topic()
                    + ", partition=" + record.partition()
                    + ", offset=" + record.offset()
                    + ", key=" + LogMaskingUtils.maskIdentifier(record.key());
        }
        return null;
    }
}
