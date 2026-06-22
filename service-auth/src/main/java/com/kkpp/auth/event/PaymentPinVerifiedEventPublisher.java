package com.kkpp.auth.event;

public interface PaymentPinVerifiedEventPublisher {

    void publish(PaymentPinVerifiedEvent event);
}
