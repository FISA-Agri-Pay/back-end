package com.kkpp.core.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.core.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.core.payment.exception.PaymentProcessingException;
import com.kkpp.core.payment.service.CreditPaymentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditPaymentRequestedConsumer {

    private final ObjectMapper objectMapper;
    private final CreditPaymentProcessingService creditPaymentProcessingService;

    @KafkaListener(
            topics = "${core.kafka.payment-request-topic:credit-payment-requested}",
            groupId = "${core.kafka.payment-request-consumer-group:service-core-payment}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            CreditPaymentRequestedMessage message = objectMapper.readValue(
                    record.value(),
                    CreditPaymentRequestedMessage.class
            );
            log.info(
                    "외상 결제 요청 Kafka 메시지를 수신했습니다. topic={}, partition={}, offset={}, key={}, eventId={}, paymentRequestPublicId={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    message.eventId(),
                    message.paymentRequestPublicId()
            );
            creditPaymentProcessingService.process(message);
        } catch (JsonProcessingException exception) {
            throw new PaymentProcessingException(
                    "결제 요청 Kafka 메시지 역직렬화에 실패했습니다. topic=" + record.topic()
                            + ", partition=" + record.partition()
                            + ", offset=" + record.offset(),
                    exception
            );
        } catch (PaymentProcessingException exception) {
            log.error(
                    "외상 결제 요청 Kafka 메시지 처리에 실패했습니다. topic={}, partition={}, offset={}, key={}, reason={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (creditPaymentProcessingService.isDuplicateKeyFailure(exception)) {
                log.info(
                        "중복 결제 요청 Kafka 메시지를 정상 처리로 간주합니다. topic={}, partition={}, offset={}, key={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        record.key()
                );
                return;
            }
            throw exception;
        }
    }
}
