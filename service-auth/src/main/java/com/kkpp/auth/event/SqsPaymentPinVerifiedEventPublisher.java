package com.kkpp.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
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
@ConditionalOnProperty(name = "payment-pin-verification.transport", havingValue = "sqs")
public class SqsPaymentPinVerifiedEventPublisher implements PaymentPinVerifiedEventPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${payment-pin-verification.sqs.queue-url}")
    private String queueUrl;

    @PostConstruct
    private void validateQueueUrl() {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("PAYMENT_PIN_VERIFIED_QUEUE_URL이 설정되지 않았습니다.");
        }
    }

    @Override
    public void publish(PaymentPinVerifiedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(payload)
                    .messageGroupId(event.userPublicId().toString())
                    .messageDeduplicationId(event.verificationId().toString())
                    .build();

            log.info(
                    "결제 PIN 검증 완료 이벤트를 SQS로 발행합니다. queueUrl={}, messageGroupId={}, messageDeduplicationId={}, eventId={}, verificationId={}",
                    queueUrl,
                    request.messageGroupId(),
                    request.messageDeduplicationId(),
                    event.eventId(),
                    event.verificationId()
            );
            SendMessageResponse response = sqsClient.sendMessage(request);
            log.info(
                    "결제 PIN 검증 완료 이벤트 SQS 발행을 완료했습니다. messageId={}, sequenceNumber={}, eventId={}, verificationId={}",
                    response.messageId(),
                    response.sequenceNumber(),
                    event.eventId(),
                    event.verificationId()
            );
        } catch (JsonProcessingException exception) {
            throw new AuthException(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED);
        } catch (SqsException exception) {
            log.error(
                    "결제 PIN 검증 완료 이벤트 SQS 발행에 실패했습니다. queueUrl={}, eventId={}, verificationId={}, awsErrorCode={}",
                    queueUrl,
                    event.eventId(),
                    event.verificationId(),
                    exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorCode() : null,
                    exception
            );
            throw new AuthException(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED);
        } catch (RuntimeException exception) {
            log.error(
                    "결제 PIN 검증 완료 이벤트 발행 중 알 수 없는 오류가 발생했습니다. queueUrl={}, eventId={}, verificationId={}",
                    queueUrl,
                    event.eventId(),
                    event.verificationId(),
                    exception
            );
            throw new AuthException(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED);
        }
    }
}
