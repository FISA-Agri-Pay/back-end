package com.kkpp.payment.event;

import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.message;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
import com.kkpp.payment.global.tracing.TracingSupport;
import com.kkpp.payment.service.CreditPaymentProcessingService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@ExtendWith(MockitoExtension.class)
class SqsCreditPaymentRequestedConsumerTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CreditPaymentProcessingService creditPaymentProcessingService;

    @Mock
    private TracingSupport tracingSupport;

    @Mock
    private Span span;

    @Mock
    private Scope scope;

    private SqsCreditPaymentRequestedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SqsCreditPaymentRequestedConsumer(
                sqsClient,
                objectMapper,
                creditPaymentProcessingService,
                tracingSupport
        );
        ReflectionTestUtils.setField(consumer, "paymentRequestQueueUrl", "https://sqs.local/payment-request");
        ReflectionTestUtils.setField(consumer, "maxNumberOfMessages", 5);
        ReflectionTestUtils.setField(consumer, "waitTimeSeconds", 10);
    }

    @Test
    void validatePropertiesRejectsInvalidQueueAndPollingSettings() {
        ReflectionTestUtils.setField(consumer, "paymentRequestQueueUrl", " ");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "validateProperties"))
                .isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(consumer, "paymentRequestQueueUrl", "https://sqs.local/payment-request");
        ReflectionTestUtils.setField(consumer, "maxNumberOfMessages", 11);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "validateProperties"))
                .isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(consumer, "maxNumberOfMessages", 5);
        ReflectionTestUtils.setField(consumer, "waitTimeSeconds", 21);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "validateProperties"))
                .isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(consumer, "waitTimeSeconds", 10);
        ReflectionTestUtils.setField(consumer, "paymentRequestQueueUrl", null);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "validateProperties"))
                .isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(consumer, "paymentRequestQueueUrl", "https://sqs.local/payment-request");
        ReflectionTestUtils.setField(consumer, "maxNumberOfMessages", 0);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "validateProperties"))
                .isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(consumer, "maxNumberOfMessages", 5);
        ReflectionTestUtils.setField(consumer, "waitTimeSeconds", -1);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "validateProperties"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatePropertiesAcceptsBoundaryPollingSettings() {
        ReflectionTestUtils.setField(consumer, "maxNumberOfMessages", 1);
        ReflectionTestUtils.setField(consumer, "waitTimeSeconds", 0);

        ReflectionTestUtils.invokeMethod(consumer, "validateProperties");

        ReflectionTestUtils.setField(consumer, "maxNumberOfMessages", 10);
        ReflectionTestUtils.setField(consumer, "waitTimeSeconds", 20);

        ReflectionTestUtils.invokeMethod(consumer, "validateProperties");
    }

    @Test
    void pollReturnsWhenNoMessagesAreReceived() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        consumer.poll();

        verify(creditPaymentProcessingService, never()).process(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollProcessesMessageAndDeletesIt() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessageWithTrace("{}");
        CreditPaymentRequestedMessage message = message();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);

        consumer.poll();

        verify(creditPaymentProcessingService).process(message);
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
        verify(span).end();
    }

    @Test
    void pollKeepsMessageWhenJsonDeserializationFails() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{bad-json}");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{bad-json}", CreditPaymentRequestedMessage.class))
                .thenThrow(new JsonParseException(null, "bad json"));

        consumer.poll();

        verify(creditPaymentProcessingService, never()).process(any());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(tracingSupport).recordException(eq(span), any(JsonParseException.class));
    }

    @Test
    void pollKeepsMessageWhenPaymentProcessingFails() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        CreditPaymentRequestedMessage message = message();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);
        doThrow(new PaymentProcessingException("failed")).when(creditPaymentProcessingService).process(message);

        consumer.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(tracingSupport).recordException(eq(span), any(PaymentProcessingException.class));
    }

    @Test
    void pollKeepsMessageWhenPaymentProcessingFailsBeforeMessageIsParsed() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        PaymentProcessingException exception = new PaymentProcessingException("parse failed");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenThrow(exception);

        consumer.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(tracingSupport).recordException(span, exception);
    }

    @Test
    void pollDeletesMessageWhenDuplicateKeyFailureOccurs() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        CreditPaymentRequestedMessage message = message();
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException(
                "duplicate",
                new SQLException("duplicate", "23505")
        );
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);
        doThrow(duplicate).when(creditPaymentProcessingService).process(message);
        when(creditPaymentProcessingService.isDuplicateKeyFailure(duplicate)).thenReturn(true);

        consumer.poll();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
        verify(tracingSupport, never()).recordException(eq(span), any(DataIntegrityViolationException.class));
    }

    @Test
    void pollDeletesMessageWhenDuplicateKeyFailureOccursBeforeMessageIsParsed() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException("duplicate");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenThrow(duplicate);
        when(creditPaymentProcessingService.isDuplicateKeyFailure(duplicate)).thenReturn(true);

        consumer.poll();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollKeepsMessageWhenDataIntegrityFailureIsNotDuplicate() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        CreditPaymentRequestedMessage message = message();
        DataIntegrityViolationException exception = new DataIntegrityViolationException("failed");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);
        doThrow(exception).when(creditPaymentProcessingService).process(message);
        when(creditPaymentProcessingService.isDuplicateKeyFailure(exception)).thenReturn(false);

        consumer.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(tracingSupport).recordException(span, exception);
    }

    @Test
    void pollKeepsMessageWhenDataIntegrityFailureOccursBeforeMessageIsParsed() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("failed");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenThrow(exception);
        when(creditPaymentProcessingService.isDuplicateKeyFailure(exception)).thenReturn(false);

        consumer.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(tracingSupport).recordException(span, exception);
    }

    @Test
    void pollHandlesSqsReceiveAndDeleteFailures() throws Exception {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(SqsException.builder().message("receive failed").build());

        consumer.poll();

        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message());
        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(SqsException.builder().message("delete failed").build());

        consumer.poll();

        verify(creditPaymentProcessingService).process(any(CreditPaymentRequestedMessage.class));
    }

    @Test
    void deleteMessageHandlesNullMessageAndAwsErrorDetails() throws Exception {
        Message sqsMessage = sqsMessage("{}");

        ReflectionTestUtils.invokeMethod(consumer, "deleteMessage", sqsMessage, null);

        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(SqsException.builder()
                        .message("delete failed")
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("ReceiptHandleIsInvalid").build())
                        .build());

        ReflectionTestUtils.invokeMethod(consumer, "deleteMessage", sqsMessage, null);
    }

    @Test
    void pollHandlesSqsFailuresWithAwsErrorDetailsAndUnexpectedReceiveError() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(SqsException.builder()
                        .message("receive failed")
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
                        .build());

        consumer.poll();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new RuntimeException("unexpected"));

        consumer.poll();
    }

    @Test
    void pollRecordsUnexpectedMessageProcessingFailure() throws Exception {
        givenTracingSpan();
        Message sqsMessage = sqsMessage("{}");
        RuntimeException exception = new RuntimeException("mapper failed");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(sqsMessage).build());
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenThrow(exception);

        consumer.poll();

        verify(tracingSupport).recordException(span, exception);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    private void givenTracingSpan() {
        when(tracingSupport.startSpan("payment.sqs.consume")).thenReturn(span);
        when(span.makeCurrent()).thenReturn(scope);
    }

    private Message sqsMessage(String body) {
        return Message.builder()
                .messageId("message-001")
                .receiptHandle("receipt-001")
                .body(body)
                .build();
    }

    private Message sqsMessageWithTrace(String body) {
        return Message.builder()
                .messageId("message-001")
                .receiptHandle("receipt-001")
                .body(body)
                .messageAttributes(java.util.Map.of(
                        "traceparent",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                                .build()
                ))
                .build();
    }
}
