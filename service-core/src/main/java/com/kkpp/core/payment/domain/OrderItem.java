package com.kkpp.core.payment.domain;

import com.kkpp.common.core.domain.BaseTimeEntity;
import com.kkpp.core.payment.dto.CreditPaymentRequestedMessage;
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
@Table(name = "order_items", schema = "core")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_public_id", referencedColumnName = "public_id", nullable = false)
    private Order order;

    @Column(name = "product_public_id", nullable = false)
    private UUID productPublicId;

    @Column(nullable = false, length = 100)
    private String productNameSnapshot;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    static OrderItem from(Order order, CreditPaymentRequestedMessage.Item item) {
        validateRequired(order, "order");
        validateRequired(item, "item");

        OrderItem orderItem = new OrderItem();
        orderItem.order = order;
        orderItem.productPublicId = item.productId();
        orderItem.productNameSnapshot = item.productName();
        orderItem.unitPriceSnapshot = item.unitPrice();
        orderItem.quantity = item.quantity();
        orderItem.totalPrice = item.totalPrice();
        return orderItem;
    }

    private static void validateRequired(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }
    }
}
