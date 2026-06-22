package com.kkpp.catalog.checkout.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.catalog.global.logging.LogMaskingUtils;
import com.kkpp.catalog.global.tracing.SqsTraceContext;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment-request.transport", havingValue = "sqs")
public class SqsCreditPaymentEventProducer implements CreditPaymentEventProducer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${payment-request.sqs.queue-url}")
    private String paymentRequestQueueUrl;

    @PostConstruct
    private void validateQueueUrl() {
        if (paymentRequestQueueUrl == null || paymentRequestQueueUrl.isBlank()) {
            throw new IllegalStateException("PAYMENT_REQUEST_QUEUE_URL이 설정되지 않았습니다.");
        }
    }

    @Override
    public void publish(CreditPaymentRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(paymentRequestQueueUrl)
                    .messageBody(payload)
                    .messageGroupId(event.userPublicId().toString())
                    .messageDeduplicationId(event.paymentRequestPublicId().toString())
                    .messageAttributes(SqsTraceContext.currentMessageAttributes())
                    .build();

            log.atInfo()
                    .addKeyValue("event", "catalog.bnpl.payment-request-event.publish.started")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("messageGroupId", LogMaskingUtils.maskIdentifier(request.messageGroupId()))
                    .addKeyValue("messageDeduplicationId", LogMaskingUtils.maskIdentifier(request.messageDeduplicationId()))
                    .addKeyValue("traceContextPropagated", request.messageAttributes().containsKey("traceparent"))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(event.paymentRequestPublicId()))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(event.orderPublicId()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("totalAmount", event.totalAmount())
                    .log("외상 결제 요청 이벤트를 SQS로 발행합니다.");

            SendMessageResponse response = sqsClient.sendMessage(request);
            log.atInfo()
                    .addKeyValue("event", "catalog.bnpl.payment-request-event.publish.completed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(response.messageId()))
                    .addKeyValue("sequenceNumber", LogMaskingUtils.maskIdentifier(response.sequenceNumber()))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(event.paymentRequestPublicId()))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(event.orderPublicId()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("resultStatus", "SUCCESS")
                    .log("외상 결제 요청 이벤트 SQS 발행이 완료되었습니다.");
        } catch (JsonProcessingException exception) {
            throw publishFailure(event, "JSON_SERIALIZATION_FAILED", exception, null);
        } catch (SqsException exception) {
            throw publishFailure(
                    event,
                    "SQS_SEND_FAILED",
                    exception,
                    exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorCode() : null
            );
        } catch (RuntimeException exception) {
            throw publishFailure(event, "UNEXPECTED_PUBLISH_ERROR", exception, null);
        }
    }

    private BusinessException publishFailure(
            CreditPaymentRequestedEvent event,
            String failureState,
            Exception exception,
            String awsErrorCode
    ) {
        log.atError()
                .addKeyValue("event", "catalog.bnpl.payment-request-event.publish.failed")
                .addKeyValue("transport", "sqs")
                .addKeyValue("queueConfigured", true)
                .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskIdentifier(event.paymentRequestPublicId()))
                .addKeyValue("orderPublicId", LogMaskingUtils.maskIdentifier(event.orderPublicId()))
                .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                .addKeyValue("failureState", failureState)
                .addKeyValue("awsErrorCode", awsErrorCode)
                .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .addKeyValue("errorMessage", ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                .setCause(exception)
                .log("외상 결제 요청 이벤트 SQS 발행에 실패했습니다.");
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 발행에 실패했습니다.");
    }
}
