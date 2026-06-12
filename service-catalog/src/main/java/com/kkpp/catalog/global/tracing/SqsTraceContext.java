package com.kkpp.catalog.global.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public final class SqsTraceContext {

    private static final TextMapSetter<Map<String, MessageAttributeValue>> SETTER =
            (carrier, key, value) -> {
                if (carrier != null && key != null && value != null) {
                    carrier.put(key, MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(value)
                            .build());
                }
            };

    private SqsTraceContext() {
    }

    /*
     * 현재 요청의 traceparent/tracestate를 SQS messageAttributes에 담습니다.
     * service-payment consumer가 이 값을 복원하면 catalog -> SQS -> payment가 하나의 trace로 이어집니다.
     */
    public static Map<String, MessageAttributeValue> currentMessageAttributes() {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), attributes, SETTER);
        return attributes;
    }
}
