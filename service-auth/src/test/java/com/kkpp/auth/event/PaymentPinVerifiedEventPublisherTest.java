package com.kkpp.auth.event;

import static com.kkpp.auth.testsupport.AuthTestEntityFactory.USER_PUBLIC_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.auth.exception.AuthErrorCode;
import com.kkpp.auth.exception.AuthException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@ExtendWith(MockitoExtension.class)
class PaymentPinVerifiedEventPublisherTest {

    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID VERIFICATION_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Mock
    private SqsClient sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    private SqsPaymentPinVerifiedEventPublisher sqsPublisher;

    @BeforeEach
    void setUp() {
        sqsPublisher = new SqsPaymentPinVerifiedEventPublisher(sqsClient, objectMapper);
        ReflectionTestUtils.setField(sqsPublisher, "queueUrl", "https://sqs.ap-northeast-2.amazonaws.com/123/queue.fifo");
    }

    @Test
    void noopPublisherAcceptsEventWithoutExternalTransport() {
        NoopPaymentPinVerifiedEventPublisher publisher = new NoopPaymentPinVerifiedEventPublisher();

        publisher.publish(event());
    }

    @Test
    void sqsPublisherSendsSerializedEvent() throws Exception {
        when(objectMapper.writeValueAsString(event())).thenReturn("{\"eventId\":\"test\"}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(SendMessageResponse.builder()
                .messageId("message-id")
                .sequenceNumber("1")
                .build());

        sqsPublisher.publish(event());

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(requestCaptor.capture());
        SendMessageRequest request = requestCaptor.getValue();
        assertThat(request.queueUrl()).contains("queue.fifo");
        assertThat(request.messageBody()).isEqualTo("{\"eventId\":\"test\"}");
        assertThat(request.messageGroupId()).isEqualTo(USER_PUBLIC_ID.toString());
        assertThat(request.messageDeduplicationId()).isEqualTo(VERIFICATION_ID.toString());
    }

    @Test
    void sqsPublisherRejectsMissingQueueUrl() {
        ReflectionTestUtils.setField(sqsPublisher, "queueUrl", " ");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(sqsPublisher, "validateQueueUrl"))
                .isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(sqsPublisher, "queueUrl", null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(sqsPublisher, "validateQueueUrl"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sqsPublisherAcceptsConfiguredQueueUrl() {
        ReflectionTestUtils.invokeMethod(sqsPublisher, "validateQueueUrl");
    }

    @Test
    void sqsPublisherWrapsJsonSerializationFailure() throws Exception {
        when(objectMapper.writeValueAsString(event())).thenThrow(new JsonProcessingException("broken") {
        });

        assertThatThrownBy(() -> sqsPublisher.publish(event()))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED));
    }

    @Test
    void sqsPublisherWrapsSqsFailure() throws Exception {
        when(objectMapper.writeValueAsString(event())).thenReturn("{\"eventId\":\"test\"}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(SqsException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
                .build());

        assertThatThrownBy(() -> sqsPublisher.publish(event()))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED));
    }

    @Test
    void sqsPublisherWrapsSqsFailureWithoutAwsErrorDetails() throws Exception {
        when(objectMapper.writeValueAsString(event())).thenReturn("{\"eventId\":\"test\"}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(SqsException.builder().build());

        assertThatThrownBy(() -> sqsPublisher.publish(event()))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED));
    }

    @Test
    void sqsPublisherWrapsUnexpectedRuntimeFailure() throws Exception {
        when(objectMapper.writeValueAsString(event())).thenReturn("{\"eventId\":\"test\"}");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(new RuntimeException("network"));

        assertThatThrownBy(() -> sqsPublisher.publish(event()))
                .isInstanceOfSatisfying(AuthException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.PAYMENT_PIN_VERIFICATION_EVENT_PUBLISH_FAILED));
    }

    private PaymentPinVerifiedEvent event() {
        return new PaymentPinVerifiedEvent(
                EVENT_ID,
                VERIFICATION_ID,
                USER_PUBLIC_ID,
                Instant.parse("2026-06-13T00:00:00Z"),
                Instant.parse("2026-06-13T00:05:00Z"),
                "PAYMENT_PIN"
        );
    }
}
