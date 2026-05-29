package com.kkpp.catalog.checkout.domain;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.common.core.domain.BaseTimeEntity;
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
@Table(name = "bnpl_payment_request_items", schema = "catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BnplPaymentRequestItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_request_public_id", referencedColumnName = "public_id", nullable = false)
    private BnplPaymentRequest paymentRequest;

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

    static BnplPaymentRequestItem from(BnplPaymentRequest paymentRequest, CartItem cartItem) {
        Product product = cartItem.getProduct();

        BnplPaymentRequestItem item = new BnplPaymentRequestItem();
        item.paymentRequest = paymentRequest;
        item.productPublicId = product.getPublicId();
        item.productNameSnapshot = product.getName();
        item.unitPriceSnapshot = product.getPrice();
        item.quantity = cartItem.getQuantity();
        item.totalPrice = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        return item;
    }
}
