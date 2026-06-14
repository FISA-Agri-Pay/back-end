package com.kkpp.catalog.checkout.domain;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PAYMENT_REQUEST_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PRODUCT_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.cartItem;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.category.domain.Category;
import com.kkpp.catalog.product.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BnplPaymentRequestDomainTest {

    @Test
    void createPaymentRequestWithItemSnapshots() {
        CartItem cartItem = cartItem(1L, USER_PUBLIC_ID, onSaleProduct(), 2);

        BnplPaymentRequest request = BnplPaymentRequest.create(
                PAYMENT_REQUEST_PUBLIC_ID,
                USER_PUBLIC_ID,
                new BigDecimal("24000"),
                List.of(cartItem)
        );

        assertThat(request.getPublicId()).isEqualTo(PAYMENT_REQUEST_PUBLIC_ID);
        assertThat(request.getRequestStatus()).isEqualTo("REQUESTED");
        assertThat(request.getTotalAmount()).isEqualByComparingTo("24000");
        assertThat(request.getItems()).hasSize(1);
        assertThat(request.getItems().getFirst().getProductPublicId()).isEqualTo(PRODUCT_PUBLIC_ID);
    }

    @Test
    void rejectMissingRequiredValuesAndMismatchedAmount() {
        CartItem cartItem = cartItem(1L, USER_PUBLIC_ID, onSaleProduct(), 2);

        assertThatThrownBy(() -> BnplPaymentRequest.create(null, USER_PUBLIC_ID, BigDecimal.ONE, List.of(cartItem)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BnplPaymentRequest.create(PAYMENT_REQUEST_PUBLIC_ID, null, BigDecimal.ONE, List.of(cartItem)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BnplPaymentRequest.create(PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID, null, List.of(cartItem)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BnplPaymentRequest.create(PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BnplPaymentRequest.create(PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID, BigDecimal.ONE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BnplPaymentRequest.create(PAYMENT_REQUEST_PUBLIC_ID, USER_PUBLIC_ID, BigDecimal.ONE, List.of(cartItem)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Product onSaleProduct() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        return product(1L, PRODUCT_PUBLIC_ID, category, "ON_SALE", 10, new BigDecimal("12000"));
    }
}
