package com.kkpp.catalog.cart.domain;

import com.kkpp.catalog.product.domain.Product;
import com.kkpp.common.core.domain.BaseEntity;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "cart_items",
        schema = "catalog",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cart_items_user_product",
                columnNames = {"user_public_id", "product_public_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_public_id", referencedColumnName = "public_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    private CartItem(UUID userPublicId, Product product, Integer quantity) {
        validatePositiveQuantity(quantity);
        this.publicId = UUID.randomUUID();
        this.userPublicId = userPublicId;
        this.product = product;
        this.quantity = quantity;
    }

    public static CartItem create(UUID userPublicId, Product product, Integer quantity) {
        return new CartItem(userPublicId, product, quantity);
    }

    public void addQuantity(Integer quantity) {
        validatePositiveQuantity(quantity);
        validatePositiveQuantity(this.quantity);
        long totalQuantity = (long) this.quantity + quantity;
        validatePositiveQuantity(totalQuantity);
        this.quantity = (int) totalQuantity;
    }

    public void changeQuantity(Integer quantity) {
        validatePositiveQuantity(quantity);
        this.quantity = quantity;
    }

    private static void validatePositiveQuantity(Integer quantity) {
        if (quantity == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "장바구니 수량은 1개 이상이어야 합니다.");
        }
        validatePositiveQuantity(quantity.longValue());
    }

    private static void validatePositiveQuantity(long quantity) {
        if (quantity < 1 || quantity > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "장바구니 수량은 1개 이상이어야 합니다.");
        }
    }
}
