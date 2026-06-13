package com.kkpp.admin.product.service;

import static com.kkpp.admin.testsupport.AdminTestEntityFactory.category;
import static com.kkpp.admin.testsupport.AdminTestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.admin.product.domain.Category;
import com.kkpp.admin.product.domain.CategoryStatus;
import com.kkpp.admin.product.domain.Product;
import com.kkpp.admin.product.domain.ProductStatus;
import com.kkpp.admin.product.dto.CreateProductRequest;
import com.kkpp.admin.product.dto.ProductResponse;
import com.kkpp.admin.product.dto.UpdateProductRequest;
import com.kkpp.admin.product.mapper.ProductMapper;
import com.kkpp.admin.product.repository.CategoryRepository;
import com.kkpp.admin.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final UUID PRODUCT_PUBLIC_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductMapper productMapper;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, productMapper);
    }

    @Test
    void createProductSavesProductWithActiveCategory() {
        Category category = category(1L, "비료", CategoryStatus.ACTIVE);
        ProductResponse response = response();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productMapper.toResponse(any(Product.class))).thenReturn(response);

        productService.createProduct(new CreateProductRequest(
                1L,
                "유기질 비료",
                "설명",
                new BigDecimal("12000"),
                10,
                "포",
                "image.png",
                ProductStatus.ON_SALE
        ));

        verify(productRepository).save(any(Product.class));
        verify(productMapper).toResponse(any(Product.class));
    }

    @Test
    void getProductsReturnsMappedPageWithNormalizedSize() {
        Product product = product(10L, category(1L, "비료", CategoryStatus.ACTIVE), ProductStatus.ON_SALE);
        when(productRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Product>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(java.util.List.of(product)));
        when(productMapper.toPageResponse(any())).thenCallRealMethod();
        when(productMapper.toResponse(product)).thenReturn(response());

        var response = productService.getProducts(null, "비료", ProductStatus.ON_SALE, " 유기질 ", -1, 0);

        org.assertj.core.api.Assertions.assertThat(response.products()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(response.page()).isZero();
        org.assertj.core.api.Assertions.assertThat(response.size()).isEqualTo(1);
    }

    @Test
    void createProductRejectsMissingOrInactiveCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.createProduct(new CreateProductRequest(
                1L, "상품", null, BigDecimal.ONE, 1, "개", null, null
        ))).isInstanceOf(BusinessException.class);

        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2L, "비활성", CategoryStatus.INACTIVE)));

        assertThatThrownBy(() -> productService.createProduct(new CreateProductRequest(
                2L, "상품", null, BigDecimal.ONE, 1, "개", null, null
        ))).isInstanceOf(BusinessException.class);
    }

    @Test
    void updateProductChangesProvidedFields() {
        Product product = product(10L, category(1L, "비료", CategoryStatus.ACTIVE), ProductStatus.ON_SALE);
        Category newCategory = category(2L, "농약", CategoryStatus.ACTIVE);
        when(productRepository.findByPublicId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
        when(productMapper.toResponse(product)).thenReturn(response());

        productService.updateProduct(PRODUCT_PUBLIC_ID, new UpdateProductRequest(
                2L,
                "수정 상품",
                null,
                new BigDecimal("15000"),
                null,
                null,
                "new.png",
                ProductStatus.SOLD_OUT
        ));

        verify(productMapper).toResponse(product);
    }

    @Test
    void stopSellingProductHidesProduct() {
        Product product = product(10L, category(1L, "비료", CategoryStatus.ACTIVE), ProductStatus.ON_SALE);
        when(productRepository.findByPublicId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));

        productService.stopSellingProduct(PRODUCT_PUBLIC_ID);

        org.assertj.core.api.Assertions.assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void deleteProductTranslatesDataIntegrityViolation() {
        Product product = product(10L, category(1L, "비료", CategoryStatus.ACTIVE), ProductStatus.ON_SALE);
        when(productRepository.findByPublicId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.of(product));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("referenced"))
                .when(productRepository).flush();

        assertThatThrownBy(() -> productService.deleteProduct(PRODUCT_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void stopSellingProductThrowsWhenProductDoesNotExist() {
        when(productRepository.findByPublicId(PRODUCT_PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.stopSellingProduct(PRODUCT_PUBLIC_ID))
                .isInstanceOf(BusinessException.class);
    }

    private ProductResponse response() {
        return new ProductResponse(
                PRODUCT_PUBLIC_ID,
                "P10010",
                1L,
                "비료",
                "유기질 비료",
                "설명",
                new BigDecimal("12000"),
                10,
                "포",
                "image.png",
                ProductStatus.ON_SALE,
                null,
                null
        );
    }
}
