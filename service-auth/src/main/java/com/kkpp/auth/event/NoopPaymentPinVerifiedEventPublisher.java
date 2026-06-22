package com.kkpp.auth.event;

import com.kkpp.auth.global.logging.LogMaskingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "payment-pin-verification.transport", havingValue = "none", matchIfMissing = true)
public class NoopPaymentPinVerifiedEventPublisher implements PaymentPinVerifiedEventPublisher {

    @Override
    public void publish(PaymentPinVerifiedEvent event) {
        // 로컬 환경처럼 이벤트 전송을 끈 상태에서 PIN 검증이 성공했음을 알려주는 로그입니다.
        log.atWarn()
                .addKeyValue("event", "auth.payment-pin.verified-event.publish.skipped")
                .addKeyValue("transport", "none")
                .addKeyValue("verificationId", LogMaskingUtils.maskIdentifier(event.verificationId()))
                .addKeyValue("userPublicId", LogMaskingUtils.maskIdentifier(event.userPublicId()))
                .log("결제 PIN 검증 완료 이벤트 발행이 비활성화되어 있습니다.");
    }
}
