package com.kkpp.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import com.kkpp.auth.global.logging.LogMaskingUtils;
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

    // PIN 검증 성공 이벤트를 SQS로 발행해 다음 결제 단계가 이어지도록 하는 publisher입니다.
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

            // SQS 발행 시작 로그입니다. queueUrl은 인프라 정보라 남기지 않고 설정 여부만 남깁니다.
            log.atInfo()
                    .addKeyValue("event", "auth.payment-pin.verified-event.publish.started")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("messageGroupId", LogMaskingUtils.maskIdentifier(request.messageGroupId()))
                    .addKeyValue("messageDeduplicationId", LogMaskingUtils.maskIdentifier(request.messageDeduplicationId()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                    .log("결제 PIN 검증 완료 이벤트를 SQS로 발행합니다.");

            SendMessageResponse response = sqsClient.sendMessage(request);
            // SQS 발행 성공 로그입니다. messageId와 verificationId는 추적용으로 마스킹합니다.
            log.atInfo()
                    .addKeyValue("event", "auth.payment-pin.verified-event.publish.completed")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(response.messageId()))
                    .addKeyValue("sequenceNumber", LogMaskingUtils.maskIdentifier(response.sequenceNumber()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                    .addKeyValue("resultStatus", "SUCCESS")
                    .log("결제 PIN 검증 완료 이벤트 SQS 발행이 완료되었습니다.");
        } catch (JsonProcessingException exception) {
            // 이벤트 객체를 JSON으로 만들지 못한 경우입니다. 메시지 본문은 로그에 남기지 않습니다.
            AuthErrorCode errorCode = AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED;
            log.atError()
                    .addKeyValue("event", "auth.payment-pin.verified-event.publish.failed")
                    .addKeyValue("failureState", "JSON_SERIALIZATION_FAILED")
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 이벤트 직렬화에 실패했습니다.");
            throw new AuthException(errorCode);
        } catch (SqsException exception) {
            // AWS SQS 호출이 실패한 경우입니다. AWS 에러 코드만 남겨 원인 분석에 사용합니다.
            AuthErrorCode errorCode = AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED;
            log.atError()
                    .addKeyValue("event", "auth.payment-pin.verified-event.publish.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                    .addKeyValue("failureState", "SQS_SEND_FAILED")
                    .addKeyValue("awsErrorCode", exception.awsErrorDetails() != null
                            ? exception.awsErrorDetails().errorCode()
                            : null)
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 이벤트 SQS 발행에 실패했습니다.");
            throw new AuthException(errorCode);
        } catch (RuntimeException exception) {
            // JSON/SQS 외 예기치 못한 발행 실패입니다.
            AuthErrorCode errorCode = AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED;
            log.atError()
                    .addKeyValue("event", "auth.payment-pin.verified-event.publish.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                    .addKeyValue("failureState", "UNEXPECTED_PUBLISH_ERROR")
                    .addKeyValue("errorCode", errorCode.getCode())
                    .addKeyValue("errorMessage", errorCode.getMessage())
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 이벤트 발행 중 예상하지 못한 오류가 발생했습니다.");
            throw new AuthException(errorCode);
        }
    }
}
