package com.kkpp.catalog.checkout.domain;

import com.kkpp.catalog.user.domain.User;
import com.kkpp.common.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "checkout_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckoutRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 20)
    private String paymentMethod;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 50)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 255)
    private String deliveryAddress;

    @Column(length = 255)
    private String deliveryAddressDetail;

    @Column(nullable = false, length = 10)
    private String deliveryZipCode;

    @Column(length = 60)
    private String orderId;

    @Column(length = 50)
    private String rejectReasonCode;

    @Column(length = 500)
    private String rejectMessage;

    private CheckoutRequest(
            User user,
            BigDecimal totalAmount,
            String paymentMethod,
            String idempotencyKey,
            String recipientName,
            String recipientPhone,
            String deliveryAddress,
            String deliveryAddressDetail,
            String deliveryZipCode
    ) {
        this.publicId = UUID.randomUUID();
        this.user = user;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.status = "PENDING";
        this.idempotencyKey = idempotencyKey;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.deliveryAddressDetail = deliveryAddressDetail;
        this.deliveryZipCode = deliveryZipCode;
    }

    public static CheckoutRequest create(
            User user,
            BigDecimal totalAmount,
            String paymentMethod,
            String idempotencyKey,
            String recipientName,
            String recipientPhone,
            String deliveryAddress,
            String deliveryAddressDetail,
            String deliveryZipCode
    ) {
        return new CheckoutRequest(
                user,
                totalAmount,
                paymentMethod,
                idempotencyKey,
                recipientName,
                recipientPhone,
                deliveryAddress,
                deliveryAddressDetail,
                deliveryZipCode
        );
    }
}
