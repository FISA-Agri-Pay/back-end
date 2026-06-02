package com.kkpp.payment.domain;

import com.kkpp.common.core.domain.BaseEntity;
import com.kkpp.payment.dto.CreditPaymentRequestedMessage;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String PREPARING = "PREPARING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "payment_request_public_id", nullable = false, unique = true)
    private UUID paymentRequestPublicId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 20)
    private String orderStatus;

    @Column(nullable = false, length = 20)
    private String deliveryStatus;

    @Column(nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false, length = 30)
    private String recipientPhone;

    @Column(nullable = false)
    private String deliveryAddress;

    @Column
    private String deliveryAddressDetail;

    @Column(nullable = false, length = 20)
    private String deliveryZipCode;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    @Column
    private LocalDateTime cancelledAt;

    @Column
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> orderItems = new ArrayList<>();

    public static Order confirmed(
            UUID orderPublicId,
            UUID userPublicId,
            UUID paymentRequestPublicId,
            BigDecimal totalAmount,
            CreditPaymentRequestedMessage.DeliveryAddress deliveryAddress,
            List<CreditPaymentRequestedMessage.Item> items,
            LocalDateTime orderedAt
    ) {
        validateRequired(orderPublicId, "orderPublicId");
        validateRequired(userPublicId, "userPublicId");
        validateRequired(paymentRequestPublicId, "paymentRequestPublicId");
        validateRequired(totalAmount, "totalAmount");
        validateRequired(deliveryAddress, "deliveryAddress");
        validateRequired(orderedAt, "orderedAt");

        Order order = new Order();
        order.publicId = orderPublicId;
        order.userPublicId = userPublicId;
        order.paymentRequestPublicId = paymentRequestPublicId;
        order.totalAmount = totalAmount;
        order.orderStatus = CONFIRMED;
        order.deliveryStatus = PREPARING;
        order.recipientName = deliveryAddress.recipientName();
        order.recipientPhone = deliveryAddress.recipientPhone();
        order.deliveryAddress = deliveryAddress.address();
        order.deliveryAddressDetail = deliveryAddress.addressDetail();
        order.deliveryZipCode = deliveryAddress.zipCode();
        order.orderedAt = orderedAt;

        if (items != null) {
            items.forEach(item -> order.orderItems.add(OrderItem.from(order, item)));
        }
        return order;
    }

    private static void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }
    }
}
