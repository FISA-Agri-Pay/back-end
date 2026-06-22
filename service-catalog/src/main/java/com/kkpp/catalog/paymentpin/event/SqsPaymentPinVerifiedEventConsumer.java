package com.kkpp.catalog.paymentpin.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.catalog.global.logging.LogMaskingUtils;
import com.kkpp.catalog.paymentpin.service.PaymentPinVerificationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment-pin-verification.consumer.enabled", havingValue = "true")
public class SqsPaymentPinVerifiedEventConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final PaymentPinVerificationService paymentPinVerificationService;

    @Value("${payment-pin-verification.sqs.queue-url}")
    private String queueUrl;

    @Value("${payment-pin-verification.sqs.max-number-of-messages:5}")
    private int maxNumberOfMessages;

    @Value("${payment-pin-verification.sqs.wait-time-seconds:20}")
    private int waitTimeSeconds;

    @PostConstruct
    private void validateProperties() {
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("PAYMENT_PIN_VERIFIED_QUEUE_URL이 설정되지 않았습니다.");
        }
        if (maxNumberOfMessages < 1 || maxNumberOfMessages > 10) {
            throw new IllegalStateException("PAYMENT_PIN_VERIFICATION_SQS_MAX_NUMBER_OF_MESSAGES는 1~10 사이여야 합니다.");
        }
        if (waitTimeSeconds < 0 || waitTimeSeconds > 20) {
            throw new IllegalStateException("PAYMENT_PIN_VERIFICATION_SQS_WAIT_TIME_SECONDS는 0~20 사이여야 합니다.");
        }
    }

    @Scheduled(fixedDelayString = "${payment-pin-verification.sqs.poll-delay-millis:1000}")
    public void poll() {
        try {
            ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(maxNumberOfMessages)
                    .waitTimeSeconds(waitTimeSeconds)
                    .build());

            for (Message sqsMessage : response.messages()) {
                processMessage(sqsMessage);
            }
        } catch (SqsException exception) {
            log.atError()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.poll.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("awsErrorCode", exception.awsErrorDetails() != null
                            ? exception.awsErrorDetails().errorCode()
                            : null)
                    .addKeyValue("failureState", "SQS_RECEIVE_FAILED")
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 SQS 메시지 수신에 실패했습니다.");
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.poll.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("failureState", "UNEXPECTED_POLL_ERROR")
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 SQS polling 중 예상하지 못한 오류가 발생했습니다.");
        }
    }

    private void processMessage(Message sqsMessage) {
        PaymentPinVerifiedEvent event = null;
        try {
            event = objectMapper.readValue(sqsMessage.body(), PaymentPinVerifiedEvent.class);
            log.atInfo()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.received")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                    .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                    .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(event.userPublicId()))
                    .log("결제 PIN 검증 완료 SQS 메시지를 수신했습니다.");

            paymentPinVerificationService.store(event);
            deleteMessage(sqsMessage, event);
        } catch (JsonProcessingException exception) {
            log.atError()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.process.failed")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("failureState", "JSON_DESERIALIZATION_FAILED")
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 SQS 메시지 역직렬화에 실패했습니다.");
        } catch (DataIntegrityViolationException exception) {
            if (event != null && paymentPinVerificationService.exists(event)) {
                log.atInfo()
                        .addKeyValue("event", "catalog.payment-pin.verified-event.duplicated")
                        .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                        .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(event.eventId()))
                        .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                        .log("중복 결제 PIN 검증 완료 SQS 메시지를 정상 처리로 간주합니다.");
                deleteMessage(sqsMessage, event);
                return;
            }
            log.atError()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.process.failed")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("eventId", event != null ? LogMaskingUtils.maskIdentifier(event.eventId()) : null)
                    .addKeyValue("verificationId", event != null ? LogMaskingUtils.maskIdentifier(event.verificationId()) : null)
                    .addKeyValue("failureState", "DATA_INTEGRITY_FAILED")
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 SQS 메시지 저장 중 데이터 제약 조건 오류가 발생했습니다.");
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.process.failed")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("eventId", event != null ? LogMaskingUtils.maskIdentifier(event.eventId()) : null)
                    .addKeyValue("verificationId", event != null ? LogMaskingUtils.maskIdentifier(event.verificationId()) : null)
                    .addKeyValue("failureState", "UNEXPECTED_PROCESS_ERROR")
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 SQS 메시지 처리 중 오류가 발생했습니다.");
        }
    }

    private void deleteMessage(Message sqsMessage, PaymentPinVerifiedEvent event) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(sqsMessage.receiptHandle())
                    .build());
            log.atInfo()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.deleted")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("eventId", event != null ? LogMaskingUtils.maskIdentifier(event.eventId()) : null)
                    .addKeyValue("verificationId", event != null ? LogMaskingUtils.maskIdentifier(event.verificationId()) : null)
                    .log("결제 PIN 검증 완료 SQS 메시지를 삭제했습니다.");
        } catch (SqsException exception) {
            log.atError()
                    .addKeyValue("event", "catalog.payment-pin.verified-event.delete.failed")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("eventId", event != null ? LogMaskingUtils.maskIdentifier(event.eventId()) : null)
                    .addKeyValue("verificationId", event != null ? LogMaskingUtils.maskIdentifier(event.verificationId()) : null)
                    .addKeyValue("awsErrorCode", exception.awsErrorDetails() != null
                            ? exception.awsErrorDetails().errorCode()
                            : null)
                    .setCause(exception)
                    .log("결제 PIN 검증 완료 SQS 메시지 삭제에 실패했습니다.");
        }
    }
}
