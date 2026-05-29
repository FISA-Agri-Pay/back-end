package com.kkpp.core.payment.repository;

import com.kkpp.core.payment.domain.PaymentEventProcessLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventProcessLogRepository extends JpaRepository<PaymentEventProcessLog, Long> {

    boolean existsByEventIdOrPaymentRequestPublicId(UUID eventId, UUID paymentRequestPublicId);
}
