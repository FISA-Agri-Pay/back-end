package com.kkpp.catalog.product.service;

import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.PRODUCT_PUBLIC_ID;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.category;
import static com.kkpp.catalog.testsupport.CatalogTestEntityFactory.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kkpp.catalog.category.domain.Category;
import com.kkpp.catalog.checkout.repository.BnplPaymentRequestItemRepository;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.catalog.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

    private static final UUID PRODUCT_ID_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID PRODUCT_ID_C = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID PRODUCT_ID_D = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BnplPaymentRequestItemRepository paymentRequestItemRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, paymentRequestItemRepository, clock);
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

    @Test
    void getRecommendationsReturnsMostPurchasedInRankOrderWithoutFallback() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        when(paymentRequestItemRepository.findTopPurchasedProductIds(any(), any(), any()))
                .thenReturn(List.of(PRODUCT_ID_A, PRODUCT_ID_B, PRODUCT_ID_C));
        when(productRepository.findSellableByPublicIds(anyList())).thenReturn(List.of(
                product(1L, PRODUCT_ID_A, category, "ON_SALE", 10, new BigDecimal("1000")),
                product(2L, PRODUCT_ID_B, category, "ON_SALE", 10, new BigDecimal("2000")),
                product(3L, PRODUCT_ID_C, category, "ON_SALE", 10, new BigDecimal("3000"))
        ));

        var response = productService.getRecommendations();

        assertThat(response).extracting(r -> r.productId())
                .containsExactly(PRODUCT_ID_A, PRODUCT_ID_B, PRODUCT_ID_C);
        verify(productRepository, org.mockito.Mockito.never()).findAllSellable();
    }

    @Test
    void getRecommendationsFillsEntirelyFromFallbackWhenNoPurchases() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        when(paymentRequestItemRepository.findTopPurchasedProductIds(any(), any(), any()))
                .thenReturn(List.of());
        when(productRepository.findAllSellable()).thenReturn(List.of(
                product(1L, PRODUCT_ID_A, category, "ON_SALE", 10, new BigDecimal("1000")),
                product(2L, PRODUCT_ID_B, category, "ON_SALE", 10, new BigDecimal("2000")),
                product(3L, PRODUCT_ID_C, category, "ON_SALE", 10, new BigDecimal("3000")),
                product(4L, PRODUCT_ID_D, category, "ON_SALE", 10, new BigDecimal("4000"))
        ));

        var response = productService.getRecommendations();

        assertThat(response).hasSize(3);
        assertThat(response).extracting(r -> r.productId())
                .doesNotHaveDuplicates()
                .allMatch(id -> List.of(PRODUCT_ID_A, PRODUCT_ID_B, PRODUCT_ID_C, PRODUCT_ID_D).contains(id));
    }

    @Test
    void getRecommendationsMergesPurchasedWithFallbackWithoutDuplicates() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        when(paymentRequestItemRepository.findTopPurchasedProductIds(any(), any(), any()))
                .thenReturn(List.of(PRODUCT_ID_A));
        when(productRepository.findSellableByPublicIds(anyList())).thenReturn(List.of(
                product(1L, PRODUCT_ID_A, category, "ON_SALE", 10, new BigDecimal("1000"))
        ));
        when(productRepository.findAllSellable()).thenReturn(List.of(
                product(1L, PRODUCT_ID_A, category, "ON_SALE", 10, new BigDecimal("1000")),
                product(2L, PRODUCT_ID_B, category, "ON_SALE", 10, new BigDecimal("2000")),
                product(3L, PRODUCT_ID_C, category, "ON_SALE", 10, new BigDecimal("3000"))
        ));

        var response = productService.getRecommendations();

        assertThat(response).hasSize(3);
        assertThat(response).extracting(r -> r.productId()).doesNotHaveDuplicates();
        assertThat(response.getFirst().productId()).isEqualTo(PRODUCT_ID_A);
    }

    @Test
    void getRecommendationsDropsTopPurchasedThatIsNoLongerSellable() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        // B는 구매 상위지만 현재 판매 불가(findSellableByPublicIds 결과에 미포함)
        when(paymentRequestItemRepository.findTopPurchasedProductIds(any(), any(), any()))
                .thenReturn(List.of(PRODUCT_ID_A, PRODUCT_ID_B));
        when(productRepository.findSellableByPublicIds(anyList())).thenReturn(List.of(
                product(1L, PRODUCT_ID_A, category, "ON_SALE", 10, new BigDecimal("1000"))
        ));
        when(productRepository.findAllSellable()).thenReturn(List.of(
                product(1L, PRODUCT_ID_A, category, "ON_SALE", 10, new BigDecimal("1000")),
                product(3L, PRODUCT_ID_C, category, "ON_SALE", 10, new BigDecimal("3000")),
                product(4L, PRODUCT_ID_D, category, "ON_SALE", 10, new BigDecimal("4000"))
        ));

        var response = productService.getRecommendations();

        assertThat(response).hasSize(3);
        assertThat(response).extracting(r -> r.productId())
                .doesNotContain(PRODUCT_ID_B)
                .contains(PRODUCT_ID_A);
    }

    @Test
    void getRecommendationsFallbackIsStableForSameDay() {
        Category category = category(1L, UUID.randomUUID(), "비료", "ACTIVE");
        when(paymentRequestItemRepository.findTopPurchasedProductIds(any(), any(), any()))
                .thenReturn(List.of());
        List<Product> candidates = List.of(
                product(1L, PRODUCT_ID_A, category, "ON_SALE", 10, new BigDecimal("1000")),
                product(2L, PRODUCT_ID_B, category, "ON_SALE", 10, new BigDecimal("2000")),
                product(3L, PRODUCT_ID_C, category, "ON_SALE", 10, new BigDecimal("3000")),
                product(4L, PRODUCT_ID_D, category, "ON_SALE", 10, new BigDecimal("4000"))
        );
        when(productRepository.findAllSellable()).thenReturn(candidates);

        var first = productService.getRecommendations();
        var second = productService.getRecommendations();

        assertThat(first).extracting(r -> r.productId())
                .isEqualTo(second.stream().map(r -> r.productId()).toList());
    }
}
