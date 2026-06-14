package com.kkpp.payment.event;

import static com.kkpp.payment.testsupport.PaymentTestEntityFactory.message;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
import com.kkpp.payment.service.CreditPaymentProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CreditPaymentRequestedConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CreditPaymentProcessingService creditPaymentProcessingService;

    private CreditPaymentRequestedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new CreditPaymentRequestedConsumer(objectMapper, creditPaymentProcessingService);
    }

    @Test
    void consumeProcessesKafkaMessage() throws Exception {
        CreditPaymentRequestedMessage message = message();
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);

        consumer.consume(record("{}"));

        verify(creditPaymentProcessingService).process(message);
    }

    @Test
    void consumeWrapsJsonFailureAsPaymentProcessingException() throws Exception {
        when(objectMapper.readValue("{bad-json}", CreditPaymentRequestedMessage.class))
                .thenThrow(new JsonParseException(null, "bad json"));

        assertThatThrownBy(() -> consumer.consume(record("{bad-json}")))
                .isInstanceOf(PaymentProcessingException.class);

        verify(creditPaymentProcessingService, never()).process(any());
    }

    @Test
    void consumeRethrowsPaymentProcessingException() throws Exception {
        CreditPaymentRequestedMessage message = message();
        PaymentProcessingException exception = new PaymentProcessingException("failed");
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);
        doThrow(exception).when(creditPaymentProcessingService).process(message);

        assertThatThrownBy(() -> consumer.consume(record("{}")))
                .isSameAs(exception);
    }

    @Test
    void consumeIgnoresDuplicateKeyFailure() throws Exception {
        CreditPaymentRequestedMessage message = message();
        DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate");
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);
        doThrow(exception).when(creditPaymentProcessingService).process(message);
        when(creditPaymentProcessingService.isDuplicateKeyFailure(exception)).thenReturn(true);

        consumer.consume(record("{}"));

        verify(creditPaymentProcessingService).isDuplicateKeyFailure(exception);
    }

    @Test
    void consumeRethrowsNonDuplicateDataIntegrityFailure() throws Exception {
        CreditPaymentRequestedMessage message = message();
        DataIntegrityViolationException exception = new DataIntegrityViolationException("failed");
        when(objectMapper.readValue("{}", CreditPaymentRequestedMessage.class)).thenReturn(message);
        doThrow(exception).when(creditPaymentProcessingService).process(message);
        when(creditPaymentProcessingService.isDuplicateKeyFailure(exception)).thenReturn(false);

        assertThatThrownBy(() -> consumer.consume(record("{}")))
                .isSameAs(exception);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("credit-payment-requested", 0, 1L, "key-001", value);
    }
}
