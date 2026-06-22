package com.kkpp.catalog.cart.domain;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PRODUCT_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kkpp.catalog.category.domain.Category;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.common.core.exception.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartItemDomainTest {

    @Test
    void createAddAndChangeQuantity() {
        Product product = onSaleProduct();

        CartItem cartItem = CartItem.create(USER_PUBLIC_ID, product, 2);
        cartItem.addQuantity(3);
        cartItem.changeQuantity(4);

        assertThat(cartItem.getPublicId()).isNotNull();
        assertThat(cartItem.getQuantity()).isEqualTo(4);
        assertThat(cartItem.getProduct()).isEqualTo(product);
    }

    @Test
    void rejectInvalidQuantity() {
        Product product = onSaleProduct();

        assertThatThrownBy(() -> CartItem.create(USER_PUBLIC_ID, product, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CartItem.create(USER_PUBLIC_ID, product, 0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> CartItem.create(USER_PUBLIC_ID, product, Integer.MAX_VALUE).addQuantity(1))
                .isInstanceOf(BusinessException.class);
    }

    private Product onSaleProduct() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        return product(1L, PRODUCT_PUBLIC_ID, category, "ON_SALE", 10, new BigDecimal("12000"));
    }
}
