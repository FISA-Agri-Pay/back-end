package com.kkpp.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
import com.kkpp.payment.global.logging.LogMaskingUtils;
import com.kkpp.payment.global.logging.MonitoredEventLogging;
import com.kkpp.payment.global.tracing.SqsTraceContext;
import com.kkpp.payment.global.tracing.TracingSupport;
import com.kkpp.payment.service.CreditPaymentProcessingService;
import io.opentelemetry.api.trace.Span;
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

    /*
     * service-catalog가 보낸 외상 결제 요청 SQS 메시지를 polling해서 DB 반영 서비스로 넘깁니다.
     * messageAttributes의 traceparent를 복원해야 catalog -> SQS -> payment가 하나의 trace로 이어집니다.
     */
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final CreditPaymentProcessingService creditPaymentProcessingService;
    private final TracingSupport tracingSupport;

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
    @MonitoredEventLogging(
            event = "payment.credit-payment-request.sqs.poll",
            operationName = "외상 결제 요청 SQS 메시지 수신",
            spanName = "service-payment.credit-payment-request.sqs.poll"
    )
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
            log.atError()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.poll.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("failureState", "SQS_RECEIVE_FAILED")
                    .addKeyValue("awsErrorCode", exception.awsErrorDetails() != null
                            ? exception.awsErrorDetails().errorCode()
                            : null)
                    .addKeyValue("errorMessage", exception.getMessage())
                    .setCause(exception)
                    .log("외상 결제 요청 SQS 메시지 수신에 실패했습니다.");
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.poll.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("queueConfigured", true)
                    .addKeyValue("failureState", "UNEXPECTED_POLL_ERROR")
                    .addKeyValue("errorMessage", exception.getMessage())
                    .setCause(exception)
                    .log("외상 결제 요청 SQS polling 중 예상하지 못한 오류가 발생했습니다.");
        }
    }

    private void processMessage(Message sqsMessage) {
        Context parentContext = SqsTraceContext.extract(sqsMessage);
        try (Scope parentScope = parentContext.makeCurrent()) {
            Span messageProcessSpan = tracingSupport.startSpan("service-payment.credit-payment-request.sqs.message.process");
            try (Scope messageScope = messageProcessSpan.makeCurrent()) {
                processMessageWithTraceContext(sqsMessage, messageProcessSpan);
            } catch (RuntimeException exception) {
                tracingSupport.recordException(messageProcessSpan, exception);
                throw exception;
            } finally {
                messageProcessSpan.end();
            }
        }
    }

    private void processMessageWithTraceContext(Message sqsMessage, Span messageProcessSpan) {
        CreditPaymentRequestedMessage message = null;
        try {
            message = objectMapper.readValue(sqsMessage.body(), CreditPaymentRequestedMessage.class);
            log.atInfo()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.message.received")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("traceContextPresent", sqsMessage.messageAttributes().containsKey("traceparent"))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(message.eventId()))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(message.paymentRequestPublicId()))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskUuid(message.orderPublicId()))
                    .log("외상 결제 요청 SQS 메시지를 수신했습니다.");

            creditPaymentProcessingService.process(message);
            deleteMessage(sqsMessage, message);
        } catch (JsonProcessingException exception) {
            tracingSupport.recordException(messageProcessSpan, exception);
            log.atError()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.message.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("traceContextPresent", sqsMessage.messageAttributes().containsKey("traceparent"))
                    .addKeyValue("failureState", "JSON_DESERIALIZATION_FAILED")
                    .addKeyValue("errorMessage", "결제 요청 SQS 메시지 본문을 해석할 수 없습니다.")
                    .setCause(exception)
                    .log("외상 결제 요청 SQS 메시지 역직렬화에 실패했습니다.");
        } catch (PaymentProcessingException exception) {
            tracingSupport.recordException(messageProcessSpan, exception);
            log.atWarn()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.message.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("paymentRequestPublicId", message != null
                            ? LogMaskingUtils.maskUuid(message.paymentRequestPublicId())
                            : null)
                    .addKeyValue("failureState", "PAYMENT_PROCESSING_FAILED")
                    .addKeyValue("errorMessage", exception.getMessage())
                    .log("외상 결제 요청 SQS 메시지 처리에 실패했습니다.");
        } catch (DataIntegrityViolationException exception) {
            if (creditPaymentProcessingService.isDuplicateKeyFailure(exception)) {
                log.atInfo()
                        .addKeyValue("event", "payment.credit-payment-request.sqs.message.duplicated")
                        .addKeyValue("transport", "sqs")
                        .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                        .addKeyValue("paymentRequestPublicId", message != null
                                ? LogMaskingUtils.maskUuid(message.paymentRequestPublicId())
                                : null)
                        .addKeyValue("resultStatus", "DUPLICATE_IGNORED")
                        .log("중복 외상 결제 요청 SQS 메시지를 정상 처리로 간주합니다.");
                deleteMessage(sqsMessage, message);
                return;
            }
            tracingSupport.recordException(messageProcessSpan, exception);
            log.atError()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.message.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("paymentRequestPublicId", message != null
                            ? LogMaskingUtils.maskUuid(message.paymentRequestPublicId())
                            : null)
                    .addKeyValue("failureState", "DATA_INTEGRITY_FAILED")
                    .addKeyValue("errorMessage", exception.getMessage())
                    .setCause(exception)
                    .log("외상 결제 요청 SQS 메시지 처리 중 데이터 제약 조건 오류가 발생했습니다.");
        } catch (RuntimeException exception) {
            tracingSupport.recordException(messageProcessSpan, exception);
            log.atError()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.message.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("paymentRequestPublicId", message != null
                            ? LogMaskingUtils.maskUuid(message.paymentRequestPublicId())
                            : null)
                    .addKeyValue("failureState", "UNEXPECTED_PROCESS_ERROR")
                    .addKeyValue("errorMessage", exception.getMessage())
                    .setCause(exception)
                    .log("외상 결제 요청 SQS 메시지 처리 중 예상하지 못한 오류가 발생했습니다.");
        }
    }

    private void deleteMessage(Message sqsMessage, CreditPaymentRequestedMessage message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(paymentRequestQueueUrl)
                    .receiptHandle(sqsMessage.receiptHandle())
                    .build());
            log.atInfo()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.message.deleted")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("paymentRequestPublicId", message != null
                            ? LogMaskingUtils.maskUuid(message.paymentRequestPublicId())
                            : null)
                    .log("외상 결제 요청 SQS 메시지를 삭제했습니다.");
        } catch (SqsException exception) {
            log.atError()
                    .addKeyValue("event", "payment.credit-payment-request.sqs.message.delete.failed")
                    .addKeyValue("transport", "sqs")
                    .addKeyValue("messageId", LogMaskingUtils.maskIdentifier(sqsMessage.messageId()))
                    .addKeyValue("paymentRequestPublicId", message != null
                            ? LogMaskingUtils.maskUuid(message.paymentRequestPublicId())
                            : null)
                    .addKeyValue("awsErrorCode", exception.awsErrorDetails() != null
                            ? exception.awsErrorDetails().errorCode()
                            : null)
                    .addKeyValue("errorMessage", exception.getMessage())
                    .setCause(exception)
                    .log("외상 결제 요청 SQS 메시지 삭제에 실패했습니다.");
        }
    }
}
