package com.kkpp.payment.domain;

import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "payment_event_process_logs",
        schema = "core",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_event_process_logs_event_id", columnNames = "event_id"),
                @UniqueConstraint(name = "uk_payment_event_process_logs_payment_request_public_id", columnNames = "payment_request_public_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEventProcessLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(name = "payment_request_public_id", nullable = false)
    private UUID paymentRequestPublicId;

    @Column(nullable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String status;

    public static PaymentEventProcessLog processed(UUID eventId, UUID paymentRequestPublicId, String idempotencyKey) {
        Objects.requireNonNull(eventId, "eventId???꾩닔?낅땲??");
        Objects.requireNonNull(paymentRequestPublicId, "paymentRequestPublicId???꾩닔?낅땲??");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey???꾩닔?낅땲??");
        }

        PaymentEventProcessLog log = new PaymentEventProcessLog();
        log.eventId = eventId;
        log.paymentRequestPublicId = paymentRequestPublicId;
        log.idempotencyKey = idempotencyKey;
        log.status = "PROCESSED";
        return log;
    }
}

