package com.kkpp.catalog.paymentpin.domain;

import com.kkpp.catalog.paymentpin.event.PaymentPinVerifiedEvent;
import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payment_pin_verifications", schema = "catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentPinVerification extends BaseEntity {

    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_USED = "USED";
    public static final String TYPE_PAYMENT_PIN = "PAYMENT_PIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "verification_id", nullable = false, unique = true)
    private UUID verificationId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "verification_type", nullable = false, length = 30)
    private String verificationType;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "payment_request_public_id")
    private UUID paymentRequestPublicId;

    public static PaymentPinVerification from(PaymentPinVerifiedEvent event) {
        Objects.requireNonNull(event, "event는 필수입니다.");
        Objects.requireNonNull(event.eventId(), "eventId는 필수입니다.");
        Objects.requireNonNull(event.verificationId(), "verificationId는 필수입니다.");
        Objects.requireNonNull(event.userPublicId(), "userPublicId는 필수입니다.");
        Objects.requireNonNull(event.verifiedAt(), "verifiedAt은 필수입니다.");
        Objects.requireNonNull(event.expiresAt(), "expiresAt은 필수입니다.");
        if (!TYPE_PAYMENT_PIN.equals(event.verificationType())) {
            throw new IllegalArgumentException("지원하지 않는 검증 타입입니다. verificationType=" + event.verificationType());
        }

        PaymentPinVerification verification = new PaymentPinVerification();
        verification.eventId = event.eventId();
        verification.verificationId = event.verificationId();
        verification.userPublicId = event.userPublicId();
        verification.verificationType = event.verificationType();
        verification.verifiedAt = LocalDateTime.ofInstant(event.verifiedAt(), ZoneOffset.UTC);
        verification.expiresAt = LocalDateTime.ofInstant(event.expiresAt(), ZoneOffset.UTC);
        verification.status = STATUS_VERIFIED;
        return verification;
    }

    public boolean isOwnedBy(UUID userPublicId) {
        return this.userPublicId.equals(userPublicId);
    }

    public boolean isVerified() {
        return STATUS_VERIFIED.equals(status);
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void markUsed(UUID paymentRequestPublicId, LocalDateTime usedAt) {
        Objects.requireNonNull(paymentRequestPublicId, "paymentRequestPublicId는 필수입니다.");
        Objects.requireNonNull(usedAt, "usedAt은 필수입니다.");
        this.status = STATUS_USED;
        this.usedAt = usedAt;
        this.paymentRequestPublicId = paymentRequestPublicId;
    }
}
