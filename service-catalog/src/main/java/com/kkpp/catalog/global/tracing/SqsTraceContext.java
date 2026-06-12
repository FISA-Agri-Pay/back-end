package com.kkpp.catalog.global.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public final class SqsTraceContext {

    public static final String ALL_MESSAGE_ATTRIBUTES = "All";

    private static final TextMapSetter<Map<String, MessageAttributeValue>> SETTER =
            (carrier, key, value) -> {
                if (carrier != null && key != null && value != null) {
                    carrier.put(key, MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(value)
                            .build());
                }
            };

    private static final TextMapGetter<Message> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Message carrier) {
            return carrier == null || carrier.messageAttributes() == null
                    ? java.util.List.of()
                    : carrier.messageAttributes().keySet();
        }

        @Override
        public String get(Message carrier, String key) {
            if (carrier == null || carrier.messageAttributes() == null) {
                return null;
            }
            MessageAttributeValue attribute = carrier.messageAttributes().get(key);
            return attribute == null ? null : attribute.stringValue();
        }
    };

    private SqsTraceContext() {
    }

    /*
     * 현재 요청의 traceparent/tracestate를 SQS 메시지 속성에 담습니다.
     * service-payment가 이 값을 복원하면 큐를 지나도 같은 trace 흐름으로 이어집니다.
     */
    public static Map<String, MessageAttributeValue> currentMessageAttributes() {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), attributes, SETTER);
        return attributes;
    }

    /*
     * SQS 메시지 속성에서 trace context를 꺼내 현재 처리 흐름의 부모 context로 사용합니다.
     */
    public static Context extract(Message message) {
        return GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), message, GETTER);
    }
}
