package com.kkpp.catalog.product.service;

import com.kkpp.catalog.checkout.repository.BnplPaymentRequestItemRepository;
import com.kkpp.catalog.product.domain.Product;
import com.kkpp.catalog.product.dto.response.ProductDetailResponse;
import com.kkpp.catalog.product.dto.response.ProductSummaryResponse;
import com.kkpp.catalog.product.repository.ProductRepository;
import com.kkpp.common.core.exception.BusinessException;
import com.kkpp.common.core.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    static final int RECOMMENDATION_SIZE = 3;

    private final ProductRepository productRepository;
    private final BnplPaymentRequestItemRepository paymentRequestItemRepository;
    private final Clock clock;

    public List<ProductSummaryResponse> getProducts(Long categoryId, String keyword) {
        return productRepository.search(categoryId, normalize(keyword)).stream()
                .map(ProductSummaryResponse::from)
                .toList();
    }

    public ProductDetailResponse getProduct(UUID productId) {
        Product product = productRepository.findByPublicIdWithCategory(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다."));
        return ProductDetailResponse.from(product);
    }

    /**
     * 홈 화면 "오늘의 추천 기자재" 영역에 노출할 상품 목록을 반환한다.
     * 오늘 승인된 구매가 많은 순으로 우선 채우고, 부족하면 날짜 고정 랜덤(판매중 & 재고 보유)으로 채워
     * 항상 {@link #RECOMMENDATION_SIZE}개를 보장한다(판매 가능한 상품이 그만큼 있을 때).
     */
    public List<ProductSummaryResponse> getRecommendations() {
        LocalDate today = LocalDate.now(clock);

        List<Product> recommendations = mostPurchasedToday(today);
        if (recommendations.size() < RECOMMENDATION_SIZE) {
            fillWithFallback(recommendations, today);
        }

        return recommendations.stream()
                .map(ProductSummaryResponse::from)
                .toList();
    }

    private List<Product> mostPurchasedToday(LocalDate today) {
        LocalDateTime startInclusive = today.atStartOfDay();
        LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();

        List<UUID> topPurchasedIds = paymentRequestItemRepository.findTopPurchasedProductIds(
                startInclusive, endExclusive, PageRequest.of(0, RECOMMENDATION_SIZE));
        if (topPurchasedIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<UUID, Product> sellableById = productRepository.findSellableByPublicIds(topPurchasedIds).stream()
                .collect(Collectors.toMap(Product::getPublicId, Function.identity()));

        // 구매 수량 순서를 유지하되, 현재 판매 가능한 상품만 노출한다.
        List<Product> ordered = new ArrayList<>();
        for (UUID productId : topPurchasedIds) {
            Product product = sellableById.get(productId);
            if (product != null) {
                ordered.add(product);
            }
        }
        return ordered;
    }

    private void fillWithFallback(List<Product> recommendations, LocalDate today) {
        Set<UUID> selectedIds = recommendations.stream()
                .map(Product::getPublicId)
                .collect(Collectors.toCollection(HashSet::new));

        List<Product> candidates = new ArrayList<>(productRepository.findAllSellable());
        // "오늘의" 추천이므로 하루 동안 동일한 결과가 노출되도록 날짜를 시드로 고정 셔플한다.
        Collections.shuffle(candidates, new Random(today.toEpochDay()));

        for (Product candidate : candidates) {
            if (recommendations.size() >= RECOMMENDATION_SIZE) {
                break;
            }
            if (selectedIds.add(candidate.getPublicId())) {
                recommendations.add(candidate);
            }
        }
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
