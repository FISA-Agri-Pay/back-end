package com.kkpp.auth.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "payment-pin-verification.transport", havingValue = "none", matchIfMissing = true)
public class NoopPaymentPinVerifiedEventPublisher implements PaymentPinVerifiedEventPublisher {

    @Override
    public void publish(PaymentPinVerifiedEvent event) {
        log.warn(
                "결제 PIN 검증 완료 이벤트 발행이 비활성화되어 있습니다. verificationId={}, userPublicId={}",
                event.verificationId(),
                event.userPublicId()
        );
    }
}
