package com.kkpp.catalog.checkout.domain;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.common.core.domain.BaseEntity;
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
@Table(name = "bnpl_payment_requests", schema = "catalog")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BnplPaymentRequest extends BaseEntity {

    private static final String REQUESTED = "REQUESTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 20)
    private String requestStatus;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column
    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToMany(mappedBy = "paymentRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<BnplPaymentRequestItem> items = new ArrayList<>();

    public static BnplPaymentRequest create(
            UUID paymentRequestPublicId,
            UUID userPublicId,
            BigDecimal totalAmount,
            List<CartItem> cartItems
    ) {
        BnplPaymentRequest request = new BnplPaymentRequest();
        request.publicId = paymentRequestPublicId;
        request.userPublicId = userPublicId;
        request.totalAmount = totalAmount;
        request.requestStatus = REQUESTED;
        request.requestedAt = LocalDateTime.now();

        cartItems.forEach(cartItem -> request.items.add(BnplPaymentRequestItem.from(request, cartItem)));
        return request;
    }
}
