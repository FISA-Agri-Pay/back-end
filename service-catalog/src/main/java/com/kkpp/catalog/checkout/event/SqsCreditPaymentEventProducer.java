package com.kkpp.catalog.checkout.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import software.amazon.awssdk.services.sqs.model.SqsException;
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
                    .build();

            log.info(
                    "외상 결제 요청 이벤트를 SQS로 발행합니다. queueUrl={}, messageGroupId={}, messageDeduplicationId={}, orderPublicId={}, eventId={}, totalAmount={}",
                    paymentRequestQueueUrl,
                    request.messageGroupId(),
                    request.messageDeduplicationId(),
                    event.orderPublicId(),
                    event.eventId(),
                    event.totalAmount()
            );
            SendMessageResponse response = sqsClient.sendMessage(request);
            log.info(
                    "외상 결제 요청 이벤트 SQS 발행을 완료했습니다. messageId={}, sequenceNumber={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}",
                    response.messageId(),
                    response.sequenceNumber(),
                    event.paymentRequestPublicId(),
                    event.orderPublicId(),
                    event.eventId()
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 생성에 실패했습니다.");
        } catch (SqsException exception) {
            log.error(
                    "외상 결제 요청 이벤트 SQS 발행에 실패했습니다. queueUrl={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}, awsErrorCode={}",
                    paymentRequestQueueUrl,
                    event.paymentRequestPublicId(),
                    event.orderPublicId(),
                    event.eventId(),
                    exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorCode() : null,
                    exception
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 발행에 실패했습니다.");
        } catch (RuntimeException exception) {
            log.error(
                    "외상 결제 요청 이벤트 SQS 발행 중 알 수 없는 오류가 발생했습니다. queueUrl={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}",
                    paymentRequestQueueUrl,
                    event.paymentRequestPublicId(),
                    event.orderPublicId(),
                    event.eventId(),
                    exception
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 발행에 실패했습니다.");
        }
    }
}
