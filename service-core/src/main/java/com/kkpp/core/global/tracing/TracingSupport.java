package com.kkpp.core.global.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

@Component
public class TracingSupport {

    private final Tracer tracer = GlobalOpenTelemetry.getTracer("service-core");

    public Span startSpan(String spanName) {
        return tracer.spanBuilder(spanName).startSpan();
    }

    public void recordException(Span span, RuntimeException exception) {
        // Custom Span 실패 상태를 명확히 표시해 Tempo에서 실패 지점을 바로 찾을 수 있게 합니다.
        span.recordException(exception);
        span.setStatus(StatusCode.ERROR, exception.getClass().getSimpleName());
    }
}
