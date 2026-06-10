package com.kkpp.catalog.checkout.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment-request.transport", havingValue = "kafka", matchIfMissing = true)
public class KafkaCreditPaymentEventProducer implements CreditPaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${catalog.kafka.payment-request-topic}")
    private String paymentRequestTopic;

    @Override
    public void publish(CreditPaymentRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info(
                    "외상 결제 요청 이벤트를 Kafka로 발행합니다. topic={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}, totalAmount={}",
                    paymentRequestTopic,
                    event.paymentRequestPublicId(),
                    event.orderPublicId(),
                    event.eventId(),
                    event.totalAmount()
            );
            SendResult<String, String> result = kafkaTemplate
                    .send(paymentRequestTopic, event.paymentRequestPublicId().toString(), payload)
                    .get();
            log.info(
                    "외상 결제 요청 이벤트 Kafka 발행을 완료했습니다. topic={}, partition={}, offset={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.paymentRequestPublicId(),
                    event.orderPublicId(),
                    event.eventId()
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 생성에 실패했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw publishFailure(event, exception);
        } catch (ExecutionException | RuntimeException exception) {
            throw publishFailure(event, exception);
        }
    }

    private BusinessException publishFailure(CreditPaymentRequestedEvent event, Exception exception) {
        log.error(
                "외상 결제 요청 이벤트 Kafka 발행에 실패했습니다. topic={}, paymentRequestPublicId={}, orderPublicId={}, eventId={}",
                paymentRequestTopic,
                event.paymentRequestPublicId(),
                event.orderPublicId(),
                event.eventId(),
                exception
        );
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 발행에 실패했습니다.");
    }
}
