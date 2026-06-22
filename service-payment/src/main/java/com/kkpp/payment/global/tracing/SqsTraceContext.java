package com.kkpp.payment.global.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

public final class SqsTraceContext {

    public static final String ALL_MESSAGE_ATTRIBUTES = "All";
    private static final String TRACEPARENT = "traceparent";

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
     * service-catalog가 SQS messageAttributes에 담은 traceparent/tracestate를 꺼냅니다.
     * traceparent가 없는 예전 메시지는 root context를 사용해 독립 trace로 처리됩니다.
     */
    public static Context extract(Message message) {
        if (!hasTraceparent(message)) {
            return Context.root();
        }
        return GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.root(), message, GETTER);
    }

    public static boolean hasTraceparent(Message message) {
        return message != null
                && message.messageAttributes() != null
                && message.messageAttributes().containsKey(TRACEPARENT);
    }
}
