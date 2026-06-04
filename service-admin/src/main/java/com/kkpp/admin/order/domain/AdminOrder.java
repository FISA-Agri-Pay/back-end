package com.kkpp.admin.order.domain;

import com.kkpp.common.core.domain.BaseEntity;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_public_id", referencedColumnName = "public_id", insertable = false, updatable = false)
    private AdminOrderUser user;

    @Column(name = "payment_request_public_id", nullable = false, unique = true)
    private UUID paymentRequestPublicId;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "delivery_address_detail")
    private String deliveryAddressDetail;

    @Column(name = "delivery_zip_code", nullable = false, length = 20)
    private String deliveryZipCode;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    public void changeDeliveryStatus(DeliveryStatus nextDeliveryStatus) {
        if (nextDeliveryStatus == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "변경할 배송 상태는 필수입니다.");
        }
        if (orderStatus == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "취소된 주문은 배송 상태를 변경할 수 없습니다.");
        }
        this.deliveryStatus = nextDeliveryStatus;
    }
}
