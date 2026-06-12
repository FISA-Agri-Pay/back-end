package com.kkpp.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import com.kkpp.payment.exception.PaymentProcessingException;
import com.kkpp.payment.global.logging.LogMaskingUtils;
import com.kkpp.payment.global.logging.MonitoredEventLogging;
import com.kkpp.payment.service.CreditPaymentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment-request.transport", havingValue = "kafka", matchIfMissing = true)
public class CreditPaymentRequestedConsumer {

    /*
     * Kafka transport를 사용하는 환경에서 외상 결제 요청 이벤트를 소비합니다.
     * Kafka는 OTel Java Agent가 headers 기반 trace context 전파를 자동 계측할 수 있어, 코드는 구조화 로그에 집중합니다.
     */
    private final ObjectMapper objectMapper;
    private final CreditPaymentProcessingService creditPaymentProcessingService;

    @KafkaListener(
            topics = "${core.kafka.payment-request-topic:credit-payment-requested}",
            groupId = "${core.kafka.payment-request-consumer-group:service-payment}"
    )
    @MonitoredEventLogging(
            event = "payment.credit-payment-request.kafka.consume",
            operationName = "외상 결제 요청 Kafka 메시지 소비",
            spanName = "service-payment.credit-payment-request.kafka.consume"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            CreditPaymentRequestedMessage message = objectMapper.readValue(
                    record.value(),
                    CreditPaymentRequestedMessage.class
            );
            log.atInfo()
                    .addKeyValue("event", "payment.credit-payment-request.kafka.message.received")
                    .addKeyValue("transport", "kafka")
                    .addKeyValue("topic", record.topic())
                    .addKeyValue("partition", record.partition())
                    .addKeyValue("offset", record.offset())
                    .addKeyValue("key", LogMaskingUtils.maskIdentifier(record.key()))
                    .addKeyValue("eventId", LogMaskingUtils.maskIdentifier(message.eventId()))
                    .addKeyValue("paymentRequestPublicId", LogMaskingUtils.maskUuid(message.paymentRequestPublicId()))
                    .addKeyValue("orderPublicId", LogMaskingUtils.maskUuid(message.orderPublicId()))
                    .log("외상 결제 요청 Kafka 메시지를 수신했습니다.");

            creditPaymentProcessingService.process(message);
        } catch (JsonProcessingException exception) {
            throw new PaymentProcessingException(
                    "결제 요청 Kafka 메시지 본문을 해석할 수 없습니다. topic=" + record.topic()
                            + ", partition=" + record.partition()
                            + ", offset=" + record.offset(),
                    exception
            );
        } catch (PaymentProcessingException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (creditPaymentProcessingService.isDuplicateKeyFailure(exception)) {
                log.atInfo()
                        .addKeyValue("event", "payment.credit-payment-request.kafka.message.duplicated")
                        .addKeyValue("transport", "kafka")
                        .addKeyValue("topic", record.topic())
                        .addKeyValue("partition", record.partition())
                        .addKeyValue("offset", record.offset())
                        .addKeyValue("key", LogMaskingUtils.maskIdentifier(record.key()))
                        .addKeyValue("resultStatus", "DUPLICATE_IGNORED")
                        .log("중복 외상 결제 요청 Kafka 메시지를 정상 처리로 간주합니다.");
                return;
            }
            throw exception;
        }
    }
}
