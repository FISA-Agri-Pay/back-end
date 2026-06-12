package com.kkpp.payment.global.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

@Component
public class TracingSupport {

    private final Tracer tracer;

    public TracingSupport() {
        this.tracer = GlobalOpenTelemetry.getTracer("service-payment");
    }

    // OTel Java Agent가 있으면 현재 trace 아래에 service-payment 전용 custom span을 생성합니다.
    public Span startSpan(String spanName) {
        if (spanName == null || spanName.isBlank()) {
            throw new IllegalArgumentException("spanName must not be null or blank");
        }
        return tracer.spanBuilder(spanName).startSpan();
    }

    // 예외를 span에도 기록해 Tempo에서 실패 지점을 바로 볼 수 있게 합니다.
    public void recordException(Span span, Throwable exception) {
        if (span == null || !span.getSpanContext().isValid()) {
            return;
        }
        span.recordException(exception);
        span.setStatus(StatusCode.ERROR, exception.getMessage());
    }
}
