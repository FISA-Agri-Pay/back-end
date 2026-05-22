package com.kkpp.catalog.checkout.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkpp.common.core.event.CreditPaymentRequestedEvent;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreditPaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${catalog.kafka.payment-request-topic}")
    private String paymentRequestTopic;

    public void publish(CreditPaymentRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            log.info(
                    "Publishing credit payment request event. topic={}, checkoutRequestId={}, eventId={}, totalAmount={}",
                    paymentRequestTopic,
                    event.checkoutRequestId(),
                    event.eventId(),
                    event.totalAmount()
            );
            kafkaTemplate.send(paymentRequestTopic, event.checkoutRequestId().toString(), payload)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error(
                                    "Failed to publish credit payment request event. topic={}, checkoutRequestId={}, eventId={}",
                                    paymentRequestTopic,
                                    event.checkoutRequestId(),
                                    event.eventId(),
                                    exception
                            );
                            return;
                        }
                        log.info(
                                "Published credit payment request event. topic={}, partition={}, offset={}, checkoutRequestId={}, eventId={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                event.checkoutRequestId(),
                                event.eventId()
                        );
                    });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "결제 요청 이벤트 생성에 실패했습니다.");
        }
    }
}
