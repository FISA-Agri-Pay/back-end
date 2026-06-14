package com.kkpp.catalog.product.service;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PRODUCT_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.catalog.category.domain.Category;
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
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void getProductsNormalizesKeywordAndMapsResponses() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        when(productRepository.search(1L, "비료"))
                .thenReturn(List.of(product(1L, PRODUCT_PUBLIC_ID, category, "ON_SALE", 10, new BigDecimal("12000"))));

        var response = productService.getProducts(1L, " 비료 ");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().productId()).isEqualTo(PRODUCT_PUBLIC_ID);
        assertThat(response.getFirst().categoryName()).isEqualTo("비료");
    }

    @Test
    void getProductsUsesNullKeywordWhenBlank() {
        when(productRepository.search(null, null)).thenReturn(List.of());

        var response = productService.getProducts(null, " ");

        assertThat(response).isEmpty();
        verify(productRepository).search(null, null);
    }

    @Test
    void getProductsUsesNullKeywordWhenKeywordIsNull() {
        when(productRepository.search(1L, null)).thenReturn(List.of());

        var response = productService.getProducts(1L, null);

        assertThat(response).isEmpty();
        verify(productRepository).search(1L, null);
    }

    @Test
    void getProductReturnsDetailOrThrows() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID))
                .thenReturn(Optional.of(product(1L, PRODUCT_PUBLIC_ID, category, "ON_SALE", 10, new BigDecimal("12000"))));

        var response = productService.getProduct(PRODUCT_PUBLIC_ID);

        assertThat(response.productId()).isEqualTo(PRODUCT_PUBLIC_ID);
        assertThat(response.categoryName()).isEqualTo("비료");

        when(productRepository.findByPublicIdWithCategory(PRODUCT_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(PRODUCT_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
    }
}
