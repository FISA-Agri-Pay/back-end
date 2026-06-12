package com.kkpp.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
import com.kkpp.payment.global.tracing.SqsTraceContext;
import com.kkpp.payment.service.CreditPaymentProcessingService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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
@ConditionalOnProperty(name = "payment-request.transport", havingValue = "sqs")
public class SqsCreditPaymentRequestedConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final CreditPaymentProcessingService creditPaymentProcessingService;

    @Value("${payment-request.sqs.queue-url}")
    private String paymentRequestQueueUrl;

    @Value("${payment-request.sqs.max-number-of-messages:5}")
    private int maxNumberOfMessages;

    @Value("${payment-request.sqs.wait-time-seconds:20}")
    private int waitTimeSeconds;

    @PostConstruct
    private void validateProperties() {
        if (paymentRequestQueueUrl == null || paymentRequestQueueUrl.isBlank()) {
            throw new IllegalStateException("PAYMENT_REQUEST_QUEUE_URL이 설정되지 않았습니다.");
        }
        if (maxNumberOfMessages < 1 || maxNumberOfMessages > 10) {
            throw new IllegalStateException("PAYMENT_REQUEST_SQS_MAX_NUMBER_OF_MESSAGES는 1~10 사이여야 합니다.");
        }
        if (waitTimeSeconds < 0 || waitTimeSeconds > 20) {
            throw new IllegalStateException("PAYMENT_REQUEST_SQS_WAIT_TIME_SECONDS는 0~20 사이여야 합니다.");
        }
    }

    @Scheduled(fixedDelayString = "${payment-request.sqs.poll-delay-millis:1000}")
    public void poll() {
        try {
            ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(paymentRequestQueueUrl)
                    .maxNumberOfMessages(maxNumberOfMessages)
                    .waitTimeSeconds(waitTimeSeconds)
                    .messageAttributeNames(SqsTraceContext.ALL_MESSAGE_ATTRIBUTES)
                    .build());

            for (Message sqsMessage : response.messages()) {
                processMessage(sqsMessage);
            }
        } catch (SqsException exception) {
            log.error(
                    "외상 결제 요청 SQS 메시지 수신에 실패했습니다. queueUrl={}, awsErrorCode={}",
                    paymentRequestQueueUrl,
                    exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorCode() : null,
                    exception
            );
        } catch (RuntimeException exception) {
            log.error(
                    "외상 결제 요청 SQS polling 중 알 수 없는 오류가 발생했습니다. queueUrl={}",
                    paymentRequestQueueUrl,
                    exception
            );
        }
    }

    private void processMessage(Message sqsMessage) {
        Context parentContext = SqsTraceContext.extract(sqsMessage);
        try (Scope ignored = parentContext.makeCurrent()) {
            processMessageWithTraceContext(sqsMessage);
        }
    }

    private void processMessageWithTraceContext(Message sqsMessage) {
        CreditPaymentRequestedMessage message = null;
        try {
            message = objectMapper.readValue(sqsMessage.body(), CreditPaymentRequestedMessage.class);
            log.info(
                    "외상 결제 요청 SQS 메시지를 수신했습니다. messageId={}, eventId={}, paymentRequestPublicId={}",
                    sqsMessage.messageId(),
                    message.eventId(),
                    message.paymentRequestPublicId()
            );

            creditPaymentProcessingService.process(message);
            deleteMessage(sqsMessage, message);
        } catch (JsonProcessingException exception) {
            log.error(
                    "외상 결제 요청 SQS 메시지 역직렬화에 실패했습니다. messageId={}",
                    sqsMessage.messageId(),
                    exception
            );
        } catch (PaymentProcessingException exception) {
            log.error(
                    "외상 결제 요청 SQS 메시지 처리에 실패했습니다. messageId={}, paymentRequestPublicId={}, reason={}",
                    sqsMessage.messageId(),
                    message != null ? message.paymentRequestPublicId() : null,
                    exception.getMessage(),
                    exception
            );
        } catch (DataIntegrityViolationException exception) {
            if (creditPaymentProcessingService.isDuplicateKeyFailure(exception)) {
                log.info(
                        "중복 결제 요청 SQS 메시지를 정상 처리로 간주합니다. messageId={}, paymentRequestPublicId={}",
                        sqsMessage.messageId(),
                        message != null ? message.paymentRequestPublicId() : null
                );
                deleteMessage(sqsMessage, message);
                return;
            }
            log.error(
                    "외상 결제 요청 SQS 메시지 처리 중 데이터 제약 조건 오류가 발생했습니다. messageId={}, paymentRequestPublicId={}",
                    sqsMessage.messageId(),
                    message != null ? message.paymentRequestPublicId() : null,
                    exception
            );
        } catch (RuntimeException exception) {
            log.error(
                    "외상 결제 요청 SQS 메시지 처리 중 알 수 없는 오류가 발생했습니다. messageId={}, paymentRequestPublicId={}",
                    sqsMessage.messageId(),
                    message != null ? message.paymentRequestPublicId() : null,
                    exception
            );
        }
    }

    private void deleteMessage(Message sqsMessage, CreditPaymentRequestedMessage message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(paymentRequestQueueUrl)
                    .receiptHandle(sqsMessage.receiptHandle())
                    .build());
            log.info(
                    "외상 결제 요청 SQS 메시지를 삭제했습니다. messageId={}, paymentRequestPublicId={}",
                    sqsMessage.messageId(),
                    message != null ? message.paymentRequestPublicId() : null
            );
        } catch (SqsException exception) {
            log.error(
                    "외상 결제 요청 SQS 메시지 삭제에 실패했습니다. messageId={}, paymentRequestPublicId={}, awsErrorCode={}",
                    sqsMessage.messageId(),
                    message != null ? message.paymentRequestPublicId() : null,
                    exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorCode() : null,
                    exception
            );
        }
    }
}
