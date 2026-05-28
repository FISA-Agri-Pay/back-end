package com.kkpp.core.payment.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "payment_event_process_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_event_process_logs_event_id", columnNames = "event_id"),
                @UniqueConstraint(name = "uk_payment_event_process_logs_checkout_request_id", columnNames = "checkout_request_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEventProcessLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String eventId;

    @Column(nullable = false)
    private UUID checkoutRequestId;

    @Column(nullable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String status;

    public static PaymentEventProcessLog processed(String eventId, UUID checkoutRequestId, String idempotencyKey) {
        PaymentEventProcessLog log = new PaymentEventProcessLog();
        log.eventId = eventId;
        log.checkoutRequestId = checkoutRequestId;
        log.idempotencyKey = idempotencyKey;
        log.status = "PROCESSED";
        return log;
    }
}
