package com.kkpp.catalog.checkout.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment-request.transport", havingValue = "sqs")
public class SqsCreditPaymentEventProducer implements CreditPaymentEventProducer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${payment-request.sqs.queue-url}")
    private String paymentRequestQueueUrl;

    @Override
    public void publish(CreditPaymentRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(paymentRequestQueueUrl)
                    .messageBody(payload)
                    .messageGroupId(event.userPublicId().toString())
                    .messageDeduplicationId(event.paymentRequestPublicId().toString())
                    .build();

            log.info(
                    "Publishing credit payment request event to SQS. queueUrl={}, messageGroupId={}, messageDeduplicationId={}, orderPublicId={}, eventId={}, totalAmount={}",
                    paymentRequestQueueUrl,
                    request.messageGroupId(),
                    request.messageDeduplicationId(),
                    event.orderPublicId(),
                    event.eventId(),
                    event.totalAmount()
            );
            SendMessageResponse response = sqsClient.sendMessage(request);
            log.info(
                    "Published credit payment request event to SQS. messageId={}, sequenceNumber={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}",
                    response.messageId(),
                    response.sequenceNumber(),
                    event.paymentRequestPublicId(),
                    event.orderPublicId(),
                    event.eventId()
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to serialize payment request event.");
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to publish credit payment request event to SQS. queueUrl={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}",
                    paymentRequestQueueUrl,
                    event.paymentRequestPublicId(),
                    event.orderPublicId(),
                    event.eventId(),
                    exception
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to publish payment request event.");
        }
    }
}
