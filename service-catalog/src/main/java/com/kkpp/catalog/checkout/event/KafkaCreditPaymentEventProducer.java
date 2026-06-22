package com.kkpp.catalog.checkout.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.catalog.global.logging.LogMaskingUtils;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment-request.transport", havingValue = "kafka", matchIfMissing = true)
public class KafkaCreditPaymentEventProducer implements CreditPaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${catalog.kafka.payment-request-topic}")
    private String paymentRequestTopic;

    @Value("${catalog.kafka.payment-request-timeout-seconds:10}")
    private Long paymentRequestTimeoutSeconds;

    @Override
    public void publish(CreditPaymentRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.atInfo()
                    .addKeyValue("event", "catalog.bnpl.payment-request-event.publish.started")
                    .addKeyValue("transport", "kafka")
                    .addKeyValue("topic", paymentRequestTopic)
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(event.paymentRequestPublicId()))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(event.orderPublicId()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("totalAmount", event.totalAmount())
                    .log("외상 결제 요청 이벤트를 Kafka로 발행합니다.");

            SendResult<String, String> result = kafkaTemplate
                    .send(paymentRequestTopic, event.paymentRequestPublicId().toString(), payload)
                    .get(paymentRequestTimeoutSeconds, TimeUnit.SECONDS);

            log.atInfo()
                    .addKeyValue("event", "catalog.bnpl.payment-request-event.publish.completed")
                    .addKeyValue("transport", "kafka")
                    .addKeyValue("topic", result.getRecordMetadata().topic())
                    .addKeyValue("partition", result.getRecordMetadata().partition())
                    .addKeyValue("offset", result.getRecordMetadata().offset())
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(event.paymentRequestPublicId()))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(event.orderPublicId()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("resultStatus", "SUCCESS")
                    .log("외상 결제 요청 이벤트 Kafka 발행이 완료되었습니다.");
        } catch (JsonProcessingException exception) {
            log.atError()
                    .addKeyValue("event", "catalog.bnpl.payment-request-event.publish.failed")
                    .addKeyValue("transport", "kafka")
                    .addKeyValue("failureState", "JSON_SERIALIZATION_FAILED")
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(event.paymentRequestPublicId()))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(event.orderPublicId()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                    .addKeyValue("errorMessage", ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                    .setCause(exception)
                    .log("외상 결제 요청 이벤트 직렬화에 실패했습니다.");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 생성에 실패했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw publishFailure(event, "KAFKA_PUBLISH_INTERRUPTED", exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw publishFailure(event, "KAFKA_PUBLISH_FAILED", exception);
        }
    }

    private BusinessException publishFailure(CreditPaymentRequestedEvent event, String failureState, Exception exception) {
        log.atError()
                .addKeyValue("event", "catalog.bnpl.payment-request-event.publish.failed")
                .addKeyValue("transport", "kafka")
                .addKeyValue("topic", paymentRequestTopic)
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(event.paymentRequestPublicId()))
                .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(event.orderPublicId()))
                .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                .addKeyValue("failureState", failureState)
                .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .addKeyValue("errorMessage", ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                .setCause(exception)
                .log("외상 결제 요청 이벤트 Kafka 발행에 실패했습니다.");
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 발행에 실패했습니다.");
    }
}
