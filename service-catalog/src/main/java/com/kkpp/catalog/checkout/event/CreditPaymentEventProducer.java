package com.kkpp.catalog.checkout.event;

import com.kkpp.common.core.event.CreditPaymentRequestedEvent;

public interface CreditPaymentEventProducer {

    void publish(CreditPaymentRequestedEvent event);
}
