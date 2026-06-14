package com.kkpp.catalog.cart.service;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PRODUCT_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.USER_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.cartItem;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.catalog.cart.domain.CartItem;
import com.kkpp.catalog.cart.dto.request.AddCartItemRequest;
import com.kkpp.catalog.cart.dto.request.UpdateCartItemQuantityRequest;
import com.kkpp.catalog.cart.repository.CartItemRepository;
import com.kkpp.catalog.category.domain.Category;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.catalog.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartItemRepository, productRepository);
    }

    @Test
    void getCartReturnsItemsAndTotalAmount() {
        Product product = onSaleProduct(10);
        when(cartItemRepository.findAllByUserPublicIdWithProduct(USER_PUBLIC_ID))
                .thenReturn(List.of(cartItem(1L, USER_PUBLIC_ID, product, 2)));

        var response = cartService.getCart(USER_PUBLIC_ID);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("24000");
    }

    @Test
    void addCartItemCreatesNewItemWhenProductIsNotInCart() {
        Product product = onSaleProduct(10);
        CartItem saved = cartItem(1L, USER_PUBLIC_ID, product, 2);
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserPublicIdAndProductPublicId(USER_PUBLIC_ID, PRODUCT_PUBLIC_ID))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(org.mockito.ArgumentMatchers.any(CartItem.class))).thenReturn(saved);

        var response = cartService.addCartItem(USER_PUBLIC_ID, new AddCartItemRequest(PRODUCT_PUBLIC_ID, 2));

        assertThat(response.quantity()).isEqualTo(2);
        verify(cartItemRepository).save(org.mockito.ArgumentMatchers.any(CartItem.class));
    }

    @Test
    void addCartItemIncreasesExistingItemQuantity() {
        Product product = onSaleProduct(10);
        CartItem existing = cartItem(1L, USER_PUBLIC_ID, product, 2);
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserPublicIdAndProductPublicId(USER_PUBLIC_ID, PRODUCT_PUBLIC_ID))
                .thenReturn(Optional.of(existing));

        var response = cartService.addCartItem(USER_PUBLIC_ID, new AddCartItemRequest(PRODUCT_PUBLIC_ID, 3));

        assertThat(response.quantity()).isEqualTo(5);
        verify(cartItemRepository, never()).save(org.mockito.ArgumentMatchers.any(CartItem.class));
    }

    @Test
    void addCartItemRejectsWhenExistingQuantityWouldExceedStock() {
        Product product = onSaleProduct(10);
        CartItem existing = cartItem(1L, USER_PUBLIC_ID, product, 8);
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByUserPublicIdAndProductPublicId(USER_PUBLIC_ID, PRODUCT_PUBLIC_ID))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cartService.addCartItem(USER_PUBLIC_ID, new AddCartItemRequest(PRODUCT_PUBLIC_ID, 3)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void addCartItemRejectsMissingProductNotOnSaleInsufficientStockAndInvalidQuantity() {
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addCartItem(USER_PUBLIC_ID, new AddCartItemRequest(PRODUCT_PUBLIC_ID, 1)))
                .isInstanceOf(BusinessException.class);

        Product hiddenProduct = product(1L, PRODUCT_PUBLIC_ID, category(1L, UUID.randomUUID(), "비료", "ACTIVE"), "HIDDEN", 10, new BigDecimal("12000"));
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(hiddenProduct));
        when(cartItemRepository.findByUserPublicIdAndProductPublicId(USER_PUBLIC_ID, PRODUCT_PUBLIC_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addCartItem(USER_PUBLIC_ID, new AddCartItemRequest(PRODUCT_PUBLIC_ID, 1)))
                .isInstanceOf(BusinessException.class);

        Product lowStock = onSaleProduct(1);
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(lowStock));

        assertThatThrownBy(() -> cartService.addCartItem(USER_PUBLIC_ID, new AddCartItemRequest(PRODUCT_PUBLIC_ID, 2)))
                .isInstanceOf(BusinessException.class);

        Product onSale = onSaleProduct(10);
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(onSale));

        assertThatThrownBy(() -> cartService.addCartItem(USER_PUBLIC_ID, new AddCartItemRequest(PRODUCT_PUBLIC_ID, 0)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateQuantityChangesExistingItemOrThrows() {
        Product product = onSaleProduct(10);
        CartItem existing = cartItem(1L, USER_PUBLIC_ID, product, 2);
        when(cartItemRepository.findByIdAndUserPublicId(1L, USER_PUBLIC_ID)).thenReturn(Optional.of(existing));

        var response = cartService.updateQuantity(USER_PUBLIC_ID, 1L, new UpdateCartItemQuantityRequest(4));

        assertThat(response.quantity()).isEqualTo(4);

        when(cartItemRepository.findByIdAndUserPublicId(2L, USER_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateQuantity(USER_PUBLIC_ID, 2L, new UpdateCartItemQuantityRequest(1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteCartItemRemovesExistingItem() {
        Product product = onSaleProduct(10);
        CartItem existing = cartItem(1L, USER_PUBLIC_ID, product, 2);
        when(cartItemRepository.findByIdAndUserPublicId(1L, USER_PUBLIC_ID)).thenReturn(Optional.of(existing));

        cartService.deleteCartItem(USER_PUBLIC_ID, 1L);

        verify(cartItemRepository).delete(existing);
    }

    private Product onSaleProduct(int stockQuantity) {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        return product(1L, PRODUCT_PUBLIC_ID, category, "ON_SALE", stockQuantity, new BigDecimal("12000"));
    }
}
